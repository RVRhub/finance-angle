# Account Tracker: Balance vs Position vs Net Worth

This document explains **how to model and present balances correctly** in an account tracker, using clear examples and Kotlin data models.

---

## 1. Core Definitions (Very Important)

### Balance
**Balance** is the amount of money in **one account at one moment**.

Examples:
- Bank account balance
- Debit card balance
- Credit card balance (amount owed)
- Loan balance (remaining debt)

> Rule: If it belongs to a single account, it *can* be called a balance.

---

### Account Position (A − P)
**Account Position** represents where you stand **across all accounts**.

```
Assets (A) − Liabilities (P) = Net Position
```

This is a **derived value**, not a balance.

---

### Net Worth
**Net Worth** includes everything:

```
Net Worth = Account Position + Investments
```

---

## 2. Example: Real Accounts

### Individual Account Balances

| Account | Type | Balance |
|------|----|----|
| Checking A | Debit | €1,200 |
| Savings B | Debit | €900 |
| Credit Card C | Credit | €800 owed |
| Loan D | Loan | €3,500 owed |

These are **true balances** (each belongs to one account).

---

## 3. Account Position Snapshot (A − P)

### Calculation

```
Assets (Debit Accounts):   €2,100
Liabilities (Credit+Loan): €4,300
--------------------------------
Account Net Position:     –€2,200
```

This number answers:
> "Where do I stand with my accounts right now?"

---

## 4. Net Worth Snapshot (Adding Investments)

Assume investments:
- ETFs & stocks: €5,000
- Crypto: €2,000

```
Account Net Position: –€2,200
Investments:          €7,000
--------------------------------
Net Worth:            €4,800
```

This answers:
> "What is my total financial position?"

---

## 5. Kotlin Data Models

### Account Snapshot (Balances)

```kotlin
data class AccountSnapshot(
    val accountId: String,
    val type: AccountType,
    val balance: MoneyAmount // true balance
)

enum class AccountType {
    DEBIT,
    CREDIT,
    LOAN
}
```

---

### Account Position Snapshot

```kotlin
data class AccountPositionSnapshot(
    val snapshotDate: LocalDate,
    val accounts: List<AccountSnapshot>,
    val totals: AccountPositionTotals
)

data class AccountPositionTotals(
    val totalAssets: MoneyAmount,
    val totalLiabilities: MoneyAmount,
    val netPosition: MoneyAmount // A − P
)
```

---

### Net Worth Snapshot

```kotlin
data class NetWorthSnapshot(
    val snapshotDate: LocalDate,
    val accountPosition: AccountPositionSnapshot,
    val investments: List<InvestmentAsset>,
    val netWorth: MoneyAmount
)
```

---

## 6. How This Appears in the UI

### Accounts Screen
**Label used:** Balance
- Checking: €1,200
- Savings: €900
- Credit card: €800 owed
- Loan: €3,500 owed

---

### Monthly Snapshot Screen
**Label used:** Account Position
- Assets: €2,100
- Liabilities: €4,300
- **Net position: –€2,200**

---

### Wealth Screen
**Label used:** Net Worth
- Accounts: –€2,200
- Investments: €7,000
- **Net worth: €4,800**

---

## 7. Golden Rule (Remember This)

> **Balance = one account**  
> **Position = many accounts**  
> **Net worth = position + investments**

Following this rule keeps your domain model correct, intuitive, and scalable.

