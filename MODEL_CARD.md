# Senior Shield Model Card

## Model
- Name: `ClutchKrishna/scam-detector-v2`
- Task: phishing / benign text classification
- Base architecture: `distilbert-base-multilingual-cased`
- Labels:
  - `benign`
  - `phishing`

## Purpose
This model is used in Senior Shield as the local AI scoring layer for suspicious SMS and shared-message review. It helps estimate phishing probability before the message is sent to the n8n reasoning layer for final explanation.

## Intended use
- SMS phishing detection
- Shared-message scam scoring
- Risk support for multilingual scam analysis

## Not intended use
- Fully autonomous law-enforcement or financial decisions
- Sole decision-maker without human-readable reasoning
- Guaranteed detection of all threat, abuse, or panic-style messages

## Training setup
- Base model: `distilbert-base-multilingual-cased`
- Dataset source: `ealvaradob/phishing-dataset`
- Training script: `train.py`
- Epochs: `2`

## Reported metrics
- Accuracy: `0.96846035015448`
- Precision: `0.9653016567677399`
- Recall: `0.9584109248913718`
- F1: `0.96184394954057`

## Strengths
- Strong phishing vs benign discrimination
- Useful as a fast first-pass scoring model
- Reusable across Android app, webhook flow, and browser-extension concepts

## Limitations
- May underperform on slang-heavy, abusive, or emotionally manipulative messages that do not look like classic phishing
- Should be combined with link analysis and explanation layers
- Should not be treated as the only safety layer

## Safety design in this project
Senior Shield does not rely on this model alone. The app combines:
- local heuristics
- link analysis
- trained phishing scoring
- webhook yes/no reasoning
- senior-friendly explanation and family alert support
