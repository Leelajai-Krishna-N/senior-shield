import json
import os
from pathlib import Path

import numpy as np
import torch
from huggingface_hub import hf_hub_download
from sklearn.metrics import accuracy_score, f1_score, precision_score, recall_score
from transformers import (
    AutoModelForSequenceClassification,
    AutoTokenizer,
    DataCollatorWithPadding,
    Trainer,
    TrainingArguments,
)
from datasets import Dataset


MODEL_NAME = "distilbert-base-multilingual-cased"
HF_REPO = "ClutchKrishna/scam-detector-v2"
DATASET_REPO = "ealvaradob/phishing-dataset"
DATASET_FILE = "combined_reduced.json"
OUTPUT_DIR = Path("./results")
MAX_LENGTH = 256


def load_json_dataset() -> Dataset:
    dataset_path = hf_hub_download(
        repo_id=DATASET_REPO,
        repo_type="dataset",
        filename=DATASET_FILE,
    )

    dataset = Dataset.from_json(dataset_path)
    if "text" not in dataset.column_names or "label" not in dataset.column_names:
        raise ValueError(f"Expected columns ['text', 'label'], found {dataset.column_names}")

    dataset = dataset.remove_columns(
        [column for column in dataset.column_names if column not in {"text", "label"}]
    )
    return dataset.class_encode_column("label") if dataset.features["label"].dtype == "string" else dataset


def preprocess_builder(tokenizer):
    def preprocess(batch):
        return tokenizer(batch["text"], truncation=True, max_length=MAX_LENGTH)

    return preprocess


def compute_metrics(eval_pred):
    logits, labels = eval_pred
    preds = np.argmax(logits, axis=-1)
    return {
        "accuracy": accuracy_score(labels, preds),
        "precision": precision_score(labels, preds, zero_division=0),
        "recall": recall_score(labels, preds, zero_division=0),
        "f1": f1_score(labels, preds, zero_division=0),
    }


def main():
    print("Using torch:", torch.__version__)
    print("CUDA available:", torch.cuda.is_available())
    if torch.cuda.is_available():
        print("GPU:", torch.cuda.get_device_name(0))

    dataset = load_json_dataset()
    split = dataset.train_test_split(test_size=0.1, seed=42)
    train_dataset = split["train"]
    test_dataset = split["test"]

    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
    preprocess = preprocess_builder(tokenizer)
    train_dataset = train_dataset.map(preprocess, batched=True)
    test_dataset = test_dataset.map(preprocess, batched=True)

    train_dataset = train_dataset.remove_columns(["text"])
    test_dataset = test_dataset.remove_columns(["text"])
    train_dataset.set_format("torch")
    test_dataset.set_format("torch")

    label2id = {"benign": 0, "phishing": 1}
    id2label = {0: "benign", 1: "phishing"}

    model = AutoModelForSequenceClassification.from_pretrained(
        MODEL_NAME,
        num_labels=2,
        label2id=label2id,
        id2label=id2label,
    )

    use_cuda = torch.cuda.is_available()
    training_args = TrainingArguments(
        output_dir=str(OUTPUT_DIR),
        do_train=True,
        do_eval=True,
        eval_strategy="steps",
        eval_steps=500,
        save_strategy="steps",
        save_steps=500,
        save_total_limit=2,
        logging_strategy="steps",
        logging_steps=100,
        per_device_train_batch_size=16 if use_cuda else 8,
        per_device_eval_batch_size=16 if use_cuda else 8,
        num_train_epochs=2,
        learning_rate=2e-5,
        weight_decay=0.01,
        fp16=use_cuda,
        bf16=False,
        gradient_accumulation_steps=1,
        push_to_hub=True,
        hub_model_id=HF_REPO,
        hub_strategy="end",
        report_to="none",
        load_best_model_at_end=True,
        metric_for_best_model="f1",
        greater_is_better=True,
    )

    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_dataset,
        eval_dataset=test_dataset,
        processing_class=tokenizer,
        data_collator=DataCollatorWithPadding(tokenizer=tokenizer),
        compute_metrics=compute_metrics,
    )

    print("Training samples:", len(train_dataset))
    print("Eval samples:", len(test_dataset))
    print("Training started...")
    train_output = trainer.train()

    metrics = trainer.evaluate()

    push_ok = True
    push_error = ""
    print("Uploading model to Hugging Face...")
    try:
        trainer.push_to_hub(commit_message="Add multilingual scam detector v2")
        tokenizer.push_to_hub(HF_REPO)
    except Exception as exc:
        push_ok = False
        push_error = str(exc)

    device_name = torch.cuda.get_device_name(0) if use_cuda else "CPU"
    final_block = {
        "model_name": MODEL_NAME,
        "hub_repo": HF_REPO,
        "device": device_name,
        "epochs_configured": float(training_args.num_train_epochs),
        "train_runtime_sec": train_output.metrics.get("train_runtime"),
        "train_samples_per_sec": train_output.metrics.get("train_samples_per_second"),
        "train_steps_per_sec": train_output.metrics.get("train_steps_per_second"),
        "train_loss": train_output.metrics.get("train_loss"),
        "eval_loss": metrics.get("eval_loss"),
        "eval_accuracy": metrics.get("eval_accuracy"),
        "eval_precision": metrics.get("eval_precision"),
        "eval_recall": metrics.get("eval_recall"),
        "eval_f1": metrics.get("eval_f1"),
        "hf_push_status": "success" if push_ok else "failed",
        "hf_push_error": push_error if not push_ok else "",
    }
    print("\n========== FINAL METRICS ==========")
    print(json.dumps(final_block, indent=2))
    print("===================================\n")


if __name__ == "__main__":
    main()
