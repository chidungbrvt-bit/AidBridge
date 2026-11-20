# AidBridge

**Bridging Verified Needs with Transparent Aid**

Organization-First, Community-Driven, Blockchain-Verifiable Social Aid Platform built for **UniHack 2026** on **Avalanche Fuji Testnet (`chainId: 43113`)**.

> **Important Notice (Simulated Data & Trust Boundary):** All households, beneficiaries, goods, and organizations in this prototype use **simulated data**. Beneficiaries do **not** need a smartphone, account, or crypto wallet. Avalanche Fuji stores an append-only cryptographic audit trail (`CaseVerified` commitments without PII); an on-chain transaction verifies record integrity and organization relayer authorization, **not** physical off-chain delivery.

---

## Live Demo & Public Links

| Resource | Link |
|---|---|
| **Live Web App (Vercel)** | https://aid-bridge-eight.vercel.app/ |
| **Backend Health Check (Render)** | https://aidbridge-my4z.onrender.com/actuator/health |
| **Smart Contract (`AidRegistry` on Fuji, Verified)** | https://testnet.snowtrace.io/address/0x015D941c679b0c402c8262BE237adAD36cEf6726 |
| **Latest Live `CaseVerified` Tx (Block `61387017`)** | https://testnet.snowtrace.io/tx/0x58f7da409adecc4d79927f811d8f7d57940ba5bdcc9cec0cd1411ea22255df81 |
| **Repository** | https://github.com/chidungbrvt-bit/AidBridge |

*(Note: Backend runs on Render Free tier with periodic health pings. If the first request after inactivity takes a few seconds, please wait briefly and refresh.)*

---

## Demo Accounts

All 4 accounts are pre-provisioned in the demo database (`Simulated beneficiary and goods data`). **Passwords are not published in this public repository** and are provided separately in the competition submission form.

| Role | Email | Capabilities in Demo |
|---|---|---|
| **Field Volunteer (Creator)** | `task1.creator@example.test` | Create simulated household cases with multi-item needs, consent, and versioned evidence fixtures; record deliveries |
| **Independent Verifier** | `task1.verifier@example.test` | Review submitted cases and approve/reject/request changes (self-approval by creator is strictly blocked) |
| **Organization Admin** | `task1.orgadmin@example.test` | Create & publish multi-item goods campaigns from verified cases; record partial goods receipts |
| **Donor** | `task1.donor@example.test` | Browse sanitized public campaigns (no beneficiary PII) and pledge integer goods quantities |

---

## Verified On-Chain Proof (Avalanche Fuji — Chain ID `43113`)

- **Contract (`AidRegistry`, non-upgradeable, Solidity `0.8.28`, `evmVersion: paris`):**
  [`0x015D941c679b0c402c8262BE237adAD36cEf6726`](https://testnet.snowtrace.io/address/0x015D941c679b0c402c8262BE237adAD36cEf6726) (Source verified on Snowtrace)
- **Organization Reference (`orgRef`):**
  `0x2395bfe7f91467046a39bd8440313164c6442839b2be6698832b7b4941b80a46`
- **Deployment Transaction:**
  [`0xf5a11026f7bea424b6ce538ab74f7618e619264e2be86d5a7407e92505b49d24`](https://testnet.snowtrace.io/tx/0xf5a11026f7bea424b6ce538ab74f7618e619264e2be86d5a7407e92505b49d24)
- **Live `CaseVerified` Transaction (from Demo Video, Block `61387017`, Status `Success`):**
  [`0x58f7da409adecc4d79927f811d8f7d57940ba5bdcc9cec0cd1411ea22255df81`](https://testnet.snowtrace.io/tx/0x58f7da409adecc4d79927f811d8f7d57940ba5bdcc9cec0cd1411ea22255df81)
- **Live `CaseVerified` Transaction (Render Smoke, Block `61384277`, Status `Success`):**
  [`0x24bb509f92f8e4038b65768c6cbd9a95b962a7f17747e54df392a4898804428f`](https://testnet.snowtrace.io/tx/0x24bb509f92f8e4038b65768c6cbd9a95b962a7f17747e54df392a4898804428f)
- **Historical `CaseVerified` Proof (Block `60893112`, Status `Success`):**
  [`0xc3b02934afb1f29816836fb22146dbc350202d3b2a9e8dbbb19c2fd2e7e8c00f`](https://testnet.snowtrace.io/tx/0xc3b02934afb1f29816836fb22146dbc350202d3b2a9e8dbbb19c2fd2e7e8c00f) (reference fixture: [`docs/fixtures/c05/fuji-case-verified.proof.json`](docs/fixtures/c05/fuji-case-verified.proof.json))
- **Relayer Key Rotation (`updateOrganizationRelayer`, Block `61383909`, Status `Success`):**
  [`0x14144c2c93939bb264ec3fe19e0c2bf0601725509b70798db2073e02a8f8ac99`](https://testnet.snowtrace.io/tx/0x14144c2c93939bb264ec3fe19e0c2bf0601725509b70798db2073e02a8f8ac99)

On-chain `CaseVerified` events publish **only** opaque `bytes32` references (`orgRef`, `caseRef`, `eventId`), `chainSequence`, salted cryptographic `evidenceCommitment`, `schemaVersion`, and block `timestamp`. Zero beneficiary PII, evidence files, private URLs, or salts are written on-chain.

---

## Architecture & Workflow

```text
Next.js (Vercel, same-origin /api/* rewrite + HttpOnly session & CSRF)
   │
   ▼
Spring Boot Modular Monolith (Render, Docker, Java 21)
   ├── auth / organization / casefile / verification
   ├── campaign / contribution / delivery / audit
   └── blockchain (durable outbox worker + Web3j + AES-256 encrypted signing state)
   │                                            │
   ▼                                            ▼
PostgreSQL (Neon, Flyway V1–V18)         Avalanche Fuji C-Chain (43113)
  - Immutable audit_events                 - AidRegistry.sol (OpenZeppelin AccessControl)
  - Transactional blockchain_jobs          - Organization-scoped relayer authorization
```

### End-to-End Business Flow
`Create Case -> Independent Verification (+ Fuji CaseVerified Anchor) -> Publish Multi-Item Campaign -> Donor Pledge Goods -> Partial Goods Receipt -> Delivery Record -> Independent Confirmation -> Public/Internal Audit`

### Key Integrity & Privacy Controls
- **Separation of Duties:** Case creator cannot self-approve (`BR-02`); delivery recorder cannot self-confirm (`BR-06`); contract `DEFAULT_ADMIN_ROLE` has no implicit business-write relayer permission (`BR-12`).
- **Four Distinct Goods Milestones:** `PLEDGED`, `RECEIVED`, `DELIVERED`, and `CONFIRMED` are tracked separately per campaign item with integer quantities and fixed units.
- **Transactional Outbox & Receipt Verification:** Business state + immutable `audit_events` + `blockchain_jobs` commit atomically in PostgreSQL; RPC calls happen outside SQL transactions. A transaction hash alone is never marked `CONFIRMED` without validating receipt status, chain, registry address, and decoded event fields (`BR-10`).
- **Privacy First:** Public campaign and audit endpoints return strictly sanitized DTOs; private evidence fixtures are served only via authenticated organization-scoped endpoints.

---

## Honest Scope & Known Limitations

- **On-Chain Milestone Scope on Fuji:** In this release, the live Avalanche Fuji worker anchors `CASE_VERIFIED` milestones (`recordCaseVerification` -> `CaseVerified`). Goods receipt, delivery, and confirmation milestones are recorded and enforced in PostgreSQL/REST APIs, while their on-chain anchoring remains `PENDING` / planned for the next contract iteration.
- **Simulated Data Only:** No real beneficiary personal data, no real monetary processing, no escrow, no tokens/NFTs, and no custom Avalanche L1.
- **Custody & Proof Boundary:** Transactions are signed by an organization-scoped backend relayer key. On-chain proof establishes immutable record integrity and relayer authorization—not independent verifier wallet signatures or physical off-chain delivery proof.

---

## Repository Structure

| Path | Description |
|---|---|
| `frontend/` | Next.js + TypeScript + Tailwind App Router frontend (deployed on Vercel) |
| `backend/aidbridge-backend/` | Spring Boot 3 / Java 21 modular monolith, Flyway migrations, JPA & Web3j worker (deployed on Render via Docker) |
| `contracts/` | Hardhat 3 + Solidity `0.8.28` (`AidRegistry.sol`), Mocha/Ethers test suites, deployment verification artifacts |
| `docs/` | Product blueprint, architecture specs, API/DB/blockchain design, security/privacy, demo scripts, and review records |

---

## Running Locally

### 1. Smart Contracts (`contracts/`)
```bash
cd contracts
npm ci
npm run compile
npm test
```

### 2. Backend (`backend/aidbridge-backend/`)
Requires **JDK 21**, **Maven**, and a local **PostgreSQL** database. Configure `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` (and keep `BLOCKCHAIN_ENABLED=false` for local DB-only runs):
```bash
cd backend/aidbridge-backend
mvn spring-boot:run
```

### 3. Frontend (`frontend/`)
Requires **Node.js 20+**. Point `API_INTERNAL_BASE_URL` to your backend (e.g., `http://localhost:8080`) and set `NEXT_PUBLIC_API_MODE=real`:
```bash
cd frontend
npm install
npm run dev
```

---

## Team

- **Dũng (B):** Backend Architecture, PostgreSQL/Flyway, Security/RBAC, Verification/Campaign APIs, Render & Neon Deployment.
- **Khoa (C):** Smart Contracts (`AidRegistry.sol`), Avalanche Fuji Deployment & Relayer Governance, Web3j/Outbox Integration & Audit Verification.
- **Minh (F):** Frontend Next.js App, UX/Wireframes, Session/CSRF Client, Demo & E2E Flows.
