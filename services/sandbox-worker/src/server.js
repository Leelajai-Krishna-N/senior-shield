import dns from "node:dns/promises";
import tls from "node:tls";
import express from "express";
import puppeteer from "puppeteer";

const app = express();
app.use(express.json({ limit: "1mb" }));

app.get("/health", (_req, res) => {
  res.json({ ok: true });
});

app.post("/scan-url", async (req, res) => {
  const targetUrl = req.body?.url;
  if (!targetUrl) {
    res.status(400).json({ error: "Missing url" });
    return;
  }

  try {
    const result = await scanUrl(targetUrl);
    res.json(result);
  } catch (error) {
    res.status(500).json({
      error: error instanceof Error ? error.message : "Unexpected scan failure",
    });
  }
});

async function scanUrl(targetUrl) {
  const parsed = new URL(targetUrl);
  const hostname = parsed.hostname;

  const [dnsData, sslData, whoisData, sandboxData] = await Promise.all([
    dnsCheck(hostname),
    sslCheck(hostname),
    whoisCheck(hostname),
    sandboxUrl(targetUrl),
  ]);

  const dom = analyzeDom(sandboxData.domData, sandboxData.finalUrl);
  const aggregate = aggregateScore({
    dns: dnsData,
    ssl: sslData,
    whois: whoisData,
    dom,
  });

  return {
    targetUrl,
    hostname,
    dns: dnsData,
    ssl: sslData,
    whois: whoisData,
    sandbox: sandboxData,
    dom,
    aggregate,
  };
}

async function dnsCheck(hostname) {
  const [a, mx, ns] = await Promise.allSettled([
    dns.resolve4(hostname),
    dns.resolveMx(hostname),
    dns.resolveNs(hostname),
  ]);

  return {
    a: a.status === "fulfilled" ? a.value : [],
    mx: mx.status === "fulfilled" ? mx.value : [],
    ns: ns.status === "fulfilled" ? ns.value : [],
  };
}

function sslCheck(hostname) {
  return new Promise((resolve) => {
    const socket = tls.connect(
      443,
      hostname,
      { servername: hostname, rejectUnauthorized: false },
      () => {
        const cert = socket.getPeerCertificate();
        const validTo = cert.valid_to || null;
        resolve({
          issuer: cert.issuer?.O || cert.issuer?.CN || null,
          subject: cert.subject?.CN || null,
          validTo,
          expired: validTo ? new Date(validTo) < new Date() : false,
          selfSigned:
            Boolean(cert.issuer?.CN) &&
            Boolean(cert.subject?.CN) &&
            cert.issuer.CN === cert.subject.CN,
          cnMismatch: Boolean(cert.subject?.CN) && !hostname.endsWith(cert.subject.CN),
        });
        socket.destroy();
      }
    );

    socket.on("error", () => resolve({ error: "SSL failed" }));
    socket.setTimeout(8000, () => {
      resolve({ error: "SSL timeout" });
      socket.destroy();
    });
  });
}

async function whoisCheck(domain) {
  const apiKey = process.env.WHOISFREAKS_API_KEY;
  if (!apiKey) {
    return { skipped: true };
  }

  try {
    const response = await fetch(
      `https://api.whoisfreaks.com/v1.0/whois?apiKey=${encodeURIComponent(apiKey)}&whois=live&domainName=${encodeURIComponent(domain)}`
    );
    const data = await response.json();
    const created = new Date(data.create_date);
    const ageDays = Number.isNaN(created.getTime())
      ? null
      : Math.floor((Date.now() - created.getTime()) / 86400000);

    return {
      registrar: data.registrar_name || null,
      ageDays,
      isNew: ageDays !== null ? ageDays < 30 : false,
      country: data.registrant_country || null,
    };
  } catch {
    return { error: "WHOIS failed" };
  }
}

async function sandboxUrl(targetUrl) {
  const browser = await puppeteer.launch({
    headless: true,
    args: [
      "--no-sandbox",
      "--disable-setuid-sandbox",
      "--disable-dev-shm-usage",
      "--disable-web-security",
    ],
  });

  const page = await browser.newPage();
  const networkLog = [];
  const redirectChain = [];

  await page.setRequestInterception(true);
  page.on("request", (request) => {
    const requestUrl = request.url();
    const type = request.resourceType();
    networkLog.push({ url: requestUrl, type });

    if (/\.(exe|zip|apk|dmg|bat|sh|msi)$/i.test(requestUrl)) {
      request.abort();
      return;
    }

    if (["xhr", "fetch", "websocket", "media"].includes(type)) {
      request.abort();
      return;
    }

    request.continue();
  });

  page.on("response", (response) => {
    if ([301, 302, 307, 308].includes(response.status())) {
      redirectChain.push({
        from: response.url(),
        to: response.headers().location || null,
      });
    }
  });

  await page.goto(targetUrl, {
    waitUntil: "networkidle2",
    timeout: 15000,
  });

  const screenshot = await page.screenshot({
    encoding: "base64",
    type: "jpeg",
    quality: 50,
  });

  const domData = await page.evaluate(() => {
    const links = [...document.querySelectorAll("a")].map((anchor) => ({
      href: anchor.href,
      text: anchor.innerText,
      download: anchor.hasAttribute("download"),
    }));
    const forms = [...document.querySelectorAll("form")].map((form) => ({
      action: form.action,
      method: form.method,
    }));
    const iframes = [...document.querySelectorAll("iframe")].map((frame) => frame.src);
    const scripts = [...document.querySelectorAll("script:not([src])")].map((script) => script.innerHTML);
    const externalScripts = [...document.querySelectorAll("script[src]")].map((script) => script.src);
    return {
      title: document.title,
      html: document.documentElement.outerHTML.slice(0, 50000),
      links,
      forms,
      iframes,
      scripts,
      externalScripts,
    };
  });

  const finalUrl = page.url();
  await browser.close();

  return {
    finalUrl,
    screenshotBase64: screenshot,
    networkLog,
    redirectChain,
    domData,
  };
}

function analyzeDom(domData, finalUrl) {
  const flags = [];
  let score = 0;
  const host = safeHost(finalUrl);

  const externalLinks = domData.links.filter((link) => safeHost(link.href) && safeHost(link.href) !== host);
  if (externalLinks.length > 10) {
    score += 20;
    flags.push("High external link count");
  }

  const downloadLinks = domData.links.filter(
    (link) => link.download || /\.(exe|zip|apk|dmg|bat|sh|msi)$/i.test(link.href || "")
  );
  if (downloadLinks.length > 0) {
    score += 30;
    flags.push(`${downloadLinks.length} download link(s) detected`);
  }

  if (domData.iframes.length > 0) {
    score += 15;
    flags.push("Iframes present");
  }

  const suspiciousForms = domData.forms.filter((form) => safeHost(form.action) && safeHost(form.action) !== host);
  if (suspiciousForms.length > 0) {
    score += 40;
    flags.push("Form submits to external domain");
  }

  const hasObfuscation = domData.scripts.some((script) => /eval\(|atob\(|unescape\(/i.test(script));
  if (hasObfuscation) {
    score += 35;
    flags.push("Obfuscated JS detected");
  }

  return {
    score: Math.min(score, 100),
    flags,
    externalLinks,
    downloadLinks,
  };
}

function aggregateScore({ ssl, whois, dom }) {
  let score = 0;
  if (whois?.isNew) score += 25;
  if (ssl?.selfSigned) score += 20;
  if (ssl?.expired) score += 15;
  if (ssl?.error) score += 20;
  if (ssl?.cnMismatch) score += 15;
  if (dom.score > 50) score += dom.score * 0.4;

  return {
    final: Math.min(Math.round(score), 100),
    verdict: score >= 70 ? "high_risk" : score >= 35 ? "caution" : "safe",
    flags: dom.flags,
  };
}

function safeHost(value) {
  try {
    return new URL(value).hostname;
  } catch {
    return "";
  }
}

const port = Number(process.env.PORT || 8080);
app.listen(port, () => {
  console.log(`Senior Shield sandbox worker listening on ${port}`);
});
