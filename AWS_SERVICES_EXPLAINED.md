# AWS Services in RepoMind

## 1. Services currently implemented

RepoMind uses four AWS services. No other AWS service is required by the current application.

| Service | What RepoMind uses it for |
| --- | --- |
| Amazon SQS | Durable background queue for repository-ingestion jobs |
| Amazon S3 | Private ZIP archive of extracted repository source files |
| Amazon SES v2 | Welcome email for a new local email/password registration |
| AWS IAM | Least-privilege permissions for the application to use SQS, S3, and SES |

Only the four services listed above are part of the current application. Re-indexing directly from an S3 archive is not implemented yet.

---

## 2. Why Amazon SQS was added

### Previous problem

Previously, `POST /api/repos/ingest` started the whole clone, chunk, embedding, and database pipeline using an in-process `@Async` thread. If the Spring Boot process restarted, crashed, or was redeployed during ingestion, that in-memory job could disappear.

### Current solution

The API now stores the repository row, sends a small job message to SQS, and returns `202 Accepted` only after SQS accepts that message.

```text
User submits repository URL
        |
        v
IngestionController creates PENDING repository row
        |
        v
IngestionQueuePublisher sends job to SQS
        |
        v
API returns 202 Accepted
        |
        v
IngestionQueueConsumer long-polls SQS
        |
        v
One of four worker threads runs IngestionService.processIngestionJob()
        |
        +--> Clone -> extract -> S3 archive -> chunk -> embed -> PostgreSQL
        |
        v
Repository becomes READY or durably recorded as FAILED
        |
        v
Consumer deletes the SQS message
```

### Durability and duplicate protection

- A message is deleted only after the job reaches a terminal database state: `READY` or `FAILED`.
- If the application dies mid-ingestion, it cannot delete the SQS message. SQS makes that message available again after the visibility timeout.
- SQS Standard queues can deliver a message more than once. RepoMind uses a PostgreSQL processing lease (`ingestion_lease_until`) so only one worker can claim a repository job at a time.
- A duplicate message for a `READY`, `FAILED`, or currently leased job is acknowledged without cloning or embedding again.
- The consumer has four worker slots. It does not pull more SQS messages than it has capacity to process, avoiding a large in-memory backlog.

### Important queue configuration

The queue visibility timeout must be longer than the longest expected ingestion. RepoMind asks SQS for a **20-minute** visibility timeout and uses a 25-minute database lease.

In the AWS Console, set the queue visibility timeout to **20 minutes or more**. If a repository can take longer, raise both values in `application.yml` before deploying.

---

## 3. Why Amazon S3 was added

The application deletes temporary cloned repositories after ingestion to avoid filling the application server disk. Before cleanup, it creates a ZIP of the extracted source files and uploads it to a private S3 bucket.

```text
repos/<repoId>/source.zip
```

The key is stored in `repositories.archive_key` only after a successful upload.

### What S3 does today

- Keeps a downloadable snapshot of the source files used for indexing.
- Prevents permanent local storage growth from GitHub clones.
- Lets an authorized user receive a private 15-minute presigned download URL through `GET /api/repos/{repoId}/archive`.

### What S3 does not do yet

- It does not retry a failed ingestion from the stored ZIP.
- It does not re-index a repository from S3.
- It does not delete an archive when the repository database row is deleted.

An S3 upload failure is intentionally best-effort: ingestion continues, but `archive_key` remains `NULL` and no archive download is available.

---

## 4. Why Amazon SES was added

Amazon SES v2 sends a welcome email after a new normal email/password registration. The email is sent with `@Async`, so registration does not wait for SES.

### Current SES behavior

| Action | Welcome email sent? |
| --- | ---: |
| New local email/password registration | Yes |
| Normal login | No |
| Google OAuth first login | No |
| GitHub OAuth first login | No |

If SES fails, registration still succeeds and a warning is logged. While SES is in sandbox mode, both the sender and a test recipient must be verified in the selected AWS Region.

---

## 5. IAM permissions

Create one dedicated IAM user for this application, such as `repomind-prod-app`. It must not have Console access or broad managed policies such as `AdministratorAccess` or `AmazonS3FullAccess`.

Attach this inline policy after replacing the placeholders:

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

Create an access key for this IAM user only when the application runs outside AWS, for example on your local computer or Render. Do not put that key in GitHub, frontend code, screenshots, or documentation.

---

## 6. AWS Console setup

### A. Cost protection first

Before creating resources, enable MFA on the AWS root account. Then create a monthly AWS Budget alert, for example at 50%, 80%, and 100% of a small monthly limit. Budgets notify you about spending; they do not immediately stop every AWS request.

### B. Create the SQS queue

1. Open **Amazon SQS** in the same region as the application, S3 bucket, and SES identity.
2. Choose **Create queue**.
3. Choose **Standard** queue.
4. Use a name such as `repomind-prod-ingestion`.
5. Set **Visibility timeout** to `20 minutes`.
6. Set **Receive message wait time** to `20 seconds` for long polling.
7. Enable server-side encryption with the SQS-managed key. This is important because private-repository jobs can contain a GitHub token.
8. Create a dead-letter queue, for example `repomind-prod-ingestion-dlq`, and configure the main queue to move a message there after 3 failed receives.
9. Copy the queue URL.

### C. Create the S3 bucket

1. Open **Amazon S3** and create a bucket, for example `repomind-prod-archives-gm-7k9x2p`.
2. Keep **Block all public access** enabled.
3. Keep ACLs disabled and use default S3-managed encryption.
4. Enable versioning only with a lifecycle rule that expires noncurrent versions, such as after 30 days.
5. Do not create a public bucket policy. Presigned URLs provide the temporary download access.

### D. Configure SES

1. Open **Amazon SES** in the same region.
2. Verify the sender email or, for production, a sender domain with DKIM.
3. While in sandbox mode, verify the test recipient email too.
4. Request production access only after the welcome-email flow is tested.

---

## 7. Application configuration

Add these values to local `.env` and to protected deployment environment variables:

```env
AWS_ACCESS_KEY_ID=your_iam_access_key_id
AWS_SECRET_ACCESS_KEY=your_iam_secret_access_key
AWS_REGION=ap-south-1
AWS_S3_BUCKET=repomind-prod-archives-gm-7k9x2p
AWS_SES_FROM_EMAIL=verified-sender@example.com
AWS_SQS_INGESTION_QUEUE_URL=https://sqs.ap-south-1.amazonaws.com/123456789012/repomind-prod-ingestion
```

`AWS_SQS_INGESTION_QUEUE_URL` is required. The application fails at startup without it because accepting an ingestion request without a durable queue would be misleading.

For unit tests only, disable the consumer to prevent a test context from polling AWS:

```env
AWS_SQS_CONSUMER_ENABLED=false
```

Do not use this setting in production.

---

## 8. How to use and test the services

### Test SQS ingestion

1. Start the application with all AWS variables configured.
2. Submit `POST /api/repos/ingest` for a small public repository.
3. Expect `202 Accepted` and repository status `PENDING`.
4. Check logs for `Queued ingestion job` and then `Picked up ingestion job`.
5. Check SQS console: the message should appear briefly, then disappear only after the job finishes.
6. Poll `GET /api/repos/{repoId}/status` until the status becomes `READY` or `FAILED`.
7. Test resilience: stop the application while a deliberately large ingestion runs. After restarting and waiting for the visibility timeout, SQS should deliver the job again.

### Test S3 archival

1. After an ingestion reaches `READY`, check `repositories.archive_key`.
2. Confirm the matching `repos/<repoId>/source.zip` object exists in S3.
3. As the repository owner, call `GET /api/repos/{repoId}/archive`.
4. Download and unzip the returned presigned URL; verify the files.
5. Call the same endpoint as a different user; it should return `404`.

### Test SES

1. Verify the sender and test-recipient identities in SES sandbox mode.
2. Register a new local email/password user.
3. Confirm the welcome email arrives.
4. Remove `ses:SendEmail` permission temporarily and repeat; registration should still succeed, with an SES warning in application logs.

---

## 9. How to pause AWS usage without deleting resources

AWS managed services are not processes that can be paused like a local Spring Boot app. The safe way to stop this application from using them is to disable the IAM access key.

1. Stop the local Spring Boot app and suspend the deployed backend.
2. AWS Console → IAM → Users → `repomind-prod-app` → **Security credentials**.
3. Under Access keys, choose **Actions → Deactivate**.

This stops the application from sending SQS messages, polling SQS, uploading/downloading S3 archives, and sending SES emails. It is reversible: activate the key again and restart the app.

Do not turn off S3 Block Public Access to pause the application; that is a security control, not a pause control.

### Important effect on queued work

If you pause an application while SQS has unprocessed messages, the messages stay in the queue until their retention period expires. When you reactivate the key and start the consumer again, it can process them. Check the queue and dead-letter queue before leaving the application paused for a long time.

---

## 10. Cost-control checklist

- Use one AWS region for SQS, S3, and SES.
- Keep SQS long polling at 20 seconds to reduce empty receive requests.
- Keep the worker count at four until database, memory, and embedding-provider capacity are measured.
- Use an S3 lifecycle rule for noncurrent object versions and incomplete multipart uploads.
- Do not enable dedicated SES IPs, SES email receiving, S3 replication, AWS Lambda, or customer-managed KMS keys unless a real requirement exists.
- Keep a Budget and Cost Anomaly alert enabled.
- If the application is stopped, deactivate the IAM key to prevent accidental requests.
- Remember that paused resources can still have storage-related costs: S3 objects and SQS messages retained in queues still exist. IAM users and inactive access keys do not create usage by themselves.

To make S3 storage cost zero, delete its stored objects and bucket. To make SQS activity stop, stop the consumer and publisher by stopping the backend or deactivating its key. Do not delete data unless you no longer need it.

---

## 11. Current limitations and next steps

| Limitation | Next step |
| --- | --- |
| The SQS message contains a GitHub token for private repositories | Keep SQS encryption enabled; later migrate private cloning to short-lived GitHub App tokens |
| A job longer than the visibility timeout can be delivered twice | Increase the queue timeout and application lease after measuring worst-case ingestion time; add lease renewal for very long jobs |
| S3 archives are not used to retry or re-index | Add a protected re-index-from-S3 workflow |
| Removing a repository does not remove its S3 archive | Define a retention policy, then implement controlled object cleanup with `s3:DeleteObject` |
| AWS integration has limited automated coverage | Add mocked SQS/S3/SES unit tests and LocalStack integration tests |

---

## Resume-ready summary

> Built a durable AWS-backed ingestion pipeline using Amazon SQS, where repository jobs are processed by bounded workers and acknowledged only after reaching a terminal database state. Integrated Amazon S3 for private source archives with presigned downloads and Amazon SES for asynchronous welcome emails, secured through least-privilege IAM policies.
