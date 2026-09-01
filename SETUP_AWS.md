# AWS Setup Guide - RepoMind

RepoMind uses Amazon SQS for durable ingestion jobs, Amazon S3 for private repository archives, Amazon SES v2 for welcome emails, and IAM for least-privilege access.

## 1. Create the SQS ingestion queue

Open the SQS console in your chosen region and create a **Standard** queue named, for example, `repomind-prod-ingestion`.

- Set the **Visibility timeout** to **20 minutes** or more.
- Set the **Receive message wait time** to **20 seconds**.
- Enable server-side encryption with the SQS-managed key.
- Create a dead-letter queue named `repomind-prod-ingestion-dlq` and configure the main queue to move messages there after 3 failed receives.
- Copy the queue URL. It becomes `AWS_SQS_INGESTION_QUEUE_URL`.

The visibility timeout must exceed the longest expected ingestion. Otherwise SQS can hand the same still-running job to a second worker.

## 2. Create the S3 archive bucket

Open the S3 console and create a bucket such as `repomind-prod-archives-gm-7k9x2p`.

- Keep **Block all public access** enabled.
- Keep ACLs disabled.
- Use default S3-managed encryption.
- Add a lifecycle rule to abort incomplete multipart uploads after 7 days.
- If versioning is enabled, expire noncurrent versions after 30 days.

The application stores private ZIP files at `repos/<repoId>/source.zip` and creates 15-minute presigned download URLs. Never make the bucket public.

## 3. Configure SES

Open Amazon SES in the same region.

1. Verify the sender email address used for `AWS_SES_FROM_EMAIL`.
2. While the account is in sandbox mode, verify a test recipient too.
3. Register a test local account to confirm the welcome email arrives.
4. For production, verify a domain with DKIM and request SES production access.

## 4. Create the IAM application user

Create an IAM user such as `repomind-prod-app` without Console access. Attach this policy after replacing placeholders:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "RepositoryArchives",
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject"],
      "Resource": "arn:aws:s3:::YOUR_BUCKET_NAME/repos/*"
    },
    {
      "Sid": "IngestionQueue",
      "Effect": "Allow",
      "Action": [
        "sqs:SendMessage",
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage"
      ],
      "Resource": "arn:aws:sqs:YOUR_REGION:YOUR_ACCOUNT_ID:YOUR_QUEUE_NAME"
    },
    {
      "Sid": "WelcomeEmail",
      "Effect": "Allow",
      "Action": "ses:SendEmail",
      "Resource": "*"
    }
  ]
}
```

Create an access key for this IAM user only when the application runs outside AWS. Never use the root account's access keys.

## 5. Configure environment variables

Set these values in local `.env` and protected deployment-environment settings:

```env
AWS_ACCESS_KEY_ID=your_iam_access_key_id
AWS_SECRET_ACCESS_KEY=your_iam_secret_access_key
AWS_REGION=ap-south-1
AWS_S3_BUCKET=repomind-prod-archives-gm-7k9x2p
AWS_SES_FROM_EMAIL=verified-sender@example.com
AWS_SQS_INGESTION_QUEUE_URL=https://sqs.ap-south-1.amazonaws.com/123456789012/repomind-prod-ingestion
```

The SQS URL is required. The application fails at startup if it is missing.

## 6. Test the integration

```powershell
.\mvnw.cmd spring-boot:run
```

1. Ingest a small public repository and expect `202 Accepted`.
2. Check logs for `Queued ingestion job` and `Picked up ingestion job`.
3. Poll the repository status until it becomes `READY` or `FAILED`.
4. Confirm the S3 archive exists after successful ingestion.
5. Register a new local user and check the SES test inbox.

## 7. Pause AWS access without deleting resources

Stop the application, then open IAM → Users → `repomind-prod-app` → Security credentials. Select the access key and choose **Deactivate**.

This stops SQS, S3, and SES API calls from the application. It is reversible: activate the key and restart the app. Stored S3 files and unprocessed SQS messages remain, so they can still create storage-related cost.

For the complete architecture, cost-control, and troubleshooting guide, see [AWS_SERVICES_EXPLAINED.md](AWS_SERVICES_EXPLAINED.md).
