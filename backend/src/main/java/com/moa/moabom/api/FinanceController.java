package com.moa.moabom.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FinanceController {
    private final JdbcTemplate jdbc;

    public FinanceController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        LocalDate now = LocalDate.now();
        LocalDate start = now.withDayOfMonth(1);
        BigDecimal income = amount("SELECT COALESCE(SUM(amount),0) FROM ledger_entries WHERE entry_type='INCOME' AND entry_date BETWEEN ? AND ?", start, now);
        BigDecimal expense = amount("SELECT COALESCE(SUM(amount),0) FROM ledger_entries WHERE entry_type='EXPENSE' AND entry_date BETWEEN ? AND ?", start, now);
        BigDecimal saving = amount("SELECT COALESCE(SUM(amount),0) FROM ledger_entries WHERE entry_type='SAVING' AND entry_date BETWEEN ? AND ?", start, now);
        BigDecimal totalSaving = amount("SELECT COALESCE(SUM(current_amount),0) FROM savings_products");
        Map<String, Object> setting = jdbc.queryForMap("SELECT * FROM app_settings LIMIT 1");
        BigDecimal budget = amount("SELECT COALESCE((SELECT amount FROM monthly_budgets WHERE month_key=?), (SELECT monthly_budget FROM app_settings LIMIT 1))", now.toString().substring(0, 7));
        BigDecimal goal = (BigDecimal) setting.get("goal_amount");
        BigDecimal monthlyAverage = amount("SELECT COALESCE(AVG(monthly_amount),0) FROM (SELECT SUM(amount) monthly_amount FROM ledger_entries WHERE entry_type='SAVING' GROUP BY DATE_TRUNC('month', entry_date)) s");
        String forecast = monthlyAverage.signum() > 0 && totalSaving.compareTo(goal) < 0
                ? LocalDate.now().plusMonths(goal.subtract(totalSaving).divide(monthlyAverage, 0, java.math.RoundingMode.CEILING).longValue()).toString()
                : "저축 기록을 추가하면 계산돼요";
        Map<String, Object> result = new HashMap<>();
        result.put("month", now.toString().substring(0, 7));
        result.put("income", income); result.put("expense", expense); result.put("saving", saving);
        result.put("totalSaving", totalSaving); result.put("goalAmount", goal);
        result.put("monthlyBudget", budget); result.put("goalDeadline", dateString(setting.get("goal_deadline"))); result.put("goalYears", setting.get("goal_years")); result.put("goalStartDate", dateString(setting.get("goal_start_date")));
        result.put("forecastDate", forecast); result.put("cardPaymentDay", setting.get("card_payment_day"));
        result.put("debts", jdbc.queryForList("SELECT id, name, balance, interest_rate, TO_CHAR(due_date,'YYYY-MM-DD') due_date, memo FROM debts ORDER BY id DESC"));
        result.put("breakdown", jdbc.queryForList("SELECT entry_type, category_name, COALESCE(detail_name, category_name) detail_name, COALESCE(SUM(amount),0) amount FROM ledger_entries WHERE entry_type IN ('EXPENSE','SAVING','DEBT_REPAYMENT') AND entry_date BETWEEN ? AND ? GROUP BY entry_type, category_name, detail_name ORDER BY amount DESC", start, now));
        return result;
    }

    @GetMapping("/entries")
    public List<Map<String, Object>> entries(@RequestParam(required=false) String month) {
        String m = month == null ? LocalDate.now().toString().substring(0, 7) : month;
        return jdbc.queryForList("SELECT e.id, TO_CHAR(e.entry_date,'YYYY-MM-DD') entry_date, e.entry_type, e.category_id, e.category_name, e.detail_name, e.amount, e.memo, e.savings_product_id, e.card_id, e.debt_id, c.color, c.emoji, card.name card_name, card.card_type FROM ledger_entries e LEFT JOIN categories c ON c.id=e.category_id LEFT JOIN cards card ON card.id=e.card_id WHERE TO_CHAR(e.entry_date,'YYYY-MM')=? ORDER BY e.entry_date DESC, e.id DESC", m);
    }

    @GetMapping("/breakdown")
    public List<Map<String, Object>> breakdown(@RequestParam String month) {
        YearMonth ym = YearMonth.parse(month);
        return jdbc.queryForList("SELECT entry_type, category_name, COALESCE(detail_name, category_name) detail_name, COALESCE(SUM(amount),0) amount FROM ledger_entries WHERE entry_type IN ('EXPENSE','SAVING','DEBT_REPAYMENT') AND entry_date BETWEEN ? AND ? GROUP BY entry_type, category_name, detail_name ORDER BY amount DESC", ym.atDay(1), ym.atEndOfMonth());
    }

    @PostMapping("/entries")
    public Map<String, Object> addEntry(@RequestBody Map<String, Object> body) {
        Number categoryId = (Number) body.get("categoryId");
        Number cardId = (Number) body.get("cardId");
        Number debtId = (Number) body.get("debtId");
        jdbc.update("INSERT INTO ledger_entries(entry_date, entry_type, category_id, category_name, detail_name, amount, memo, savings_product_id, card_id, debt_id) VALUES (?,?,?,?,?,?,?,?,?,?)",
                LocalDate.parse(body.get("date").toString()), body.get("type"), categoryId == null ? null : categoryId.longValue(), body.get("category").toString(), body.get("detail"), new BigDecimal(body.get("amount").toString()), body.get("memo"), body.get("savingsProductId"), cardId == null ? null : cardId.longValue(), debtId == null ? null : debtId.longValue());
        if ("SAVING".equals(body.get("type")) && body.get("savingsProductId") != null)
            jdbc.update("UPDATE savings_products SET current_amount=current_amount+? WHERE id=?", new BigDecimal(body.get("amount").toString()), body.get("savingsProductId"));
        adjustDebtPayment(body, 1);
        return Map.of("ok", true);
    }

    @PutMapping("/entries/{id}")
    public Map<String, Object> updateEntry(@PathVariable long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> old = jdbc.queryForMap("SELECT * FROM ledger_entries WHERE id=?", id);
        Number categoryId = (Number) body.get("categoryId"); Number cardId = (Number) body.get("cardId"); Number debtId = (Number) body.get("debtId");
        jdbc.update("UPDATE ledger_entries SET entry_date=?, entry_type=?, category_id=?, category_name=?, detail_name=?, amount=?, memo=?, savings_product_id=?, card_id=?, debt_id=? WHERE id=?",
                LocalDate.parse(body.get("date").toString()), body.get("type"), categoryId == null ? null : categoryId.longValue(), body.get("category"), body.get("detail"), new BigDecimal(body.get("amount").toString()), body.get("memo"), body.get("savingsProductId"), cardId == null ? null : cardId.longValue(), debtId == null ? null : debtId.longValue(), id);
        adjustSavingBalance(old, -1); adjustSavingBalance(body, 1);
        adjustDebtPayment(old, -1); adjustDebtPayment(body, 1);
        return Map.of("ok", true);
    }

    @DeleteMapping("/entries/{id}")
    public Map<String, Object> deleteEntry(@PathVariable long id) { Map<String,Object> old = jdbc.queryForMap("SELECT * FROM ledger_entries WHERE id=?", id); adjustSavingBalance(old, -1); adjustDebtPayment(old, -1); jdbc.update("DELETE FROM ledger_entries WHERE id=?", id); return Map.of("ok", true); }

    private void adjustSavingBalance(Map<String, Object> row, int sign) {
        if ("SAVING".equals(row.get("entry_type")) && row.get("savings_product_id") != null)
            jdbc.update("UPDATE savings_products SET current_amount=current_amount+? WHERE id=?", new BigDecimal(row.get("amount").toString()).multiply(BigDecimal.valueOf(sign)), row.get("savings_product_id"));
        else if ("SAVING".equals(row.get("type")) && row.get("savingsProductId") != null)
            jdbc.update("UPDATE savings_products SET current_amount=current_amount+? WHERE id=?", new BigDecimal(row.get("amount").toString()).multiply(BigDecimal.valueOf(sign)), row.get("savingsProductId"));
    }

    private void adjustDebtPayment(Map<String, Object> row, int sign) {
        Object type = row.get("entry_type") != null ? row.get("entry_type") : row.get("type");
        Object debt = row.get("debt_id") != null ? row.get("debt_id") : row.get("debtId");
        if (!"DEBT_REPAYMENT".equals(type) || debt == null) return;
        String dateValue = row.get("entry_date") != null ? row.get("entry_date").toString() : row.get("date").toString();
        String monthKey = dateValue.substring(0, 7);
        BigDecimal delta = new BigDecimal(row.get("amount").toString()).multiply(BigDecimal.valueOf(sign));
        jdbc.update("INSERT INTO debt_payments(debt_id,month_key,amount) VALUES (?,?,?) ON CONFLICT (debt_id,month_key) DO UPDATE SET amount=debt_payments.amount+EXCLUDED.amount", debt, monthKey, delta);
        jdbc.update("DELETE FROM debt_payments WHERE debt_id=? AND month_key=? AND amount<=0", debt, monthKey);
    }

    @GetMapping("/categories")
    public List<Map<String,Object>> categories() { return jdbc.queryForList("SELECT * FROM categories ORDER BY category_type, name, detail_name"); }

    @PostMapping("/categories")
    public Map<String,Object> addCategory(@RequestBody Map<String,Object> body) { jdbc.update("INSERT INTO categories(name,category_type,color,emoji,detail_name) VALUES (?,?,?,?,?)", body.get("name"), body.get("type"), body.getOrDefault("color", "#7DBA8B"), body.getOrDefault("emoji", "🌱"), body.get("detail")); return Map.of("ok", true); }

    @PutMapping("/categories/{id}")
    public Map<String,Object> updateCategory(@PathVariable long id, @RequestBody Map<String,Object> body) { jdbc.update("UPDATE categories SET name=?,category_type=?,color=?,emoji=?,detail_name=? WHERE id=?", body.get("name"), body.get("type"), body.getOrDefault("color", "#7DBA8B"), body.getOrDefault("emoji", "🌱"), body.get("detail"), id); return Map.of("ok", true); }

    @DeleteMapping("/categories/{id}")
    public Map<String,Object> deleteCategory(@PathVariable long id) { jdbc.update("DELETE FROM categories WHERE id=?", id); return Map.of("ok", true); }

    @GetMapping("/savings-products")
    public List<Map<String,Object>> savingsProducts() { return jdbc.queryForList("SELECT id, name, product_type, interest_rate, TO_CHAR(maturity_date,'YYYY-MM-DD') maturity_date, current_amount, target_amount, memo FROM savings_products ORDER BY maturity_date NULLS LAST, id"); }

    @PostMapping("/savings-products")
    public Map<String,Object> addSavings(@RequestBody Map<String,Object> b) { jdbc.update("INSERT INTO savings_products(name,product_type,interest_rate,maturity_date,current_amount,target_amount,memo) VALUES (?,?,?,?,?,?,?)", b.get("name"), b.getOrDefault("type", "적금"), new BigDecimal(b.getOrDefault("rate", "0").toString()), dateOrNull(b.get("maturityDate")), new BigDecimal(b.getOrDefault("amount", "0").toString()), b.get("targetAmount"), b.get("memo")); return Map.of("ok", true); }

    @PutMapping("/savings-products/{id}")
    public Map<String,Object> updateSavings(@PathVariable long id, @RequestBody Map<String,Object> b) { jdbc.update("UPDATE savings_products SET name=?,product_type=?,interest_rate=?,maturity_date=?,current_amount=? WHERE id=?", b.get("name"), b.get("type"), new BigDecimal(b.getOrDefault("rate", "0").toString()), dateOrNull(b.get("maturityDate")), new BigDecimal(b.getOrDefault("amount", "0").toString()), id); return Map.of("ok", true); }

    @DeleteMapping("/savings-products/{id}")
    public Map<String,Object> deleteSavings(@PathVariable long id) { jdbc.update("DELETE FROM savings_products WHERE id=?", id); return Map.of("ok", true); }

    @GetMapping("/debts")
    public List<Map<String,Object>> debts() { return jdbc.queryForList("SELECT d.id, d.name, d.balance, d.interest_rate, TO_CHAR(d.due_date,'YYYY-MM-DD') due_date, d.memo, COALESCE((SELECT SUM(amount) FROM debt_payments p WHERE p.debt_id=d.id),0) paid_amount FROM debts d ORDER BY id DESC"); }

    @PostMapping("/debts")
    public Map<String,Object> addDebt(@RequestBody Map<String,Object> b) { jdbc.update("INSERT INTO debts(name,balance,interest_rate,due_date,memo) VALUES (?,?,?,?,?)", b.get("name"), new BigDecimal(b.getOrDefault("balance", "0").toString()), new BigDecimal(b.getOrDefault("rate", "0").toString()), dateOrNull(b.get("dueDate")), b.get("memo")); return Map.of("ok", true); }

    @PostMapping("/debts/{id}/payments")
    public Map<String,Object> addDebtPayment(@PathVariable long id, @RequestBody Map<String,Object> b) { jdbc.update("INSERT INTO debt_payments(debt_id,month_key,amount) VALUES (?,?,?) ON CONFLICT (debt_id,month_key) DO UPDATE SET amount=EXCLUDED.amount", id, b.get("month"), new BigDecimal(b.get("amount").toString())); return Map.of("ok", true); }

    @PutMapping("/debts/{id}")
    public Map<String,Object> updateDebt(@PathVariable long id, @RequestBody Map<String,Object> b) { jdbc.update("UPDATE debts SET name=?,balance=?,interest_rate=?,due_date=? WHERE id=?", b.get("name"), new BigDecimal(b.getOrDefault("balance", "0").toString()), new BigDecimal(b.getOrDefault("rate", "0").toString()), dateOrNull(b.get("dueDate")), id); return Map.of("ok", true); }

    @DeleteMapping("/debts/{id}")
    public Map<String,Object> deleteDebt(@PathVariable long id) { jdbc.update("DELETE FROM debts WHERE id=?", id); return Map.of("ok", true); }

    @GetMapping("/cards")
    public List<Map<String,Object>> cards() { return jdbc.queryForList("SELECT * FROM cards ORDER BY is_default DESC, id"); }

    @PostMapping("/cards")
    public Map<String,Object> addCard(@RequestBody Map<String,Object> b) { boolean isDefault = Boolean.parseBoolean(b.getOrDefault("isDefault", false).toString()); if (isDefault) jdbc.update("UPDATE cards SET is_default=FALSE"); jdbc.update("INSERT INTO cards(name,card_type,is_default) VALUES (?,?,?)", b.get("name"), b.getOrDefault("type", "체크카드"), isDefault); return Map.of("ok", true); }

    @PutMapping("/cards/{id}/default")
    public Map<String,Object> setDefaultCard(@PathVariable long id) { jdbc.update("UPDATE cards SET is_default=FALSE"); jdbc.update("UPDATE cards SET is_default=TRUE WHERE id=?", id); return Map.of("ok", true); }

    @GetMapping("/settings")
    public Map<String,Object> settings() { Map<String,Object> result = jdbc.queryForMap("SELECT * FROM app_settings LIMIT 1"); result.put("goal_start_date", dateString(result.get("goal_start_date"))); result.put("goal_deadline", dateString(result.get("goal_deadline"))); return result; }

    @PutMapping("/settings")
    public Map<String,Object> updateSettings(@RequestBody Map<String,Object> b) { jdbc.update("UPDATE app_settings SET goal_amount=?,goal_years=?,goal_start_date=?,card_payment_day=? WHERE id=(SELECT id FROM app_settings LIMIT 1)", new BigDecimal(b.get("goalAmount").toString()), Integer.parseInt(b.get("goalYears").toString()), dateOrNull(b.get("goalStartDate")), b.get("cardPaymentDay")); return Map.of("ok", true); }

    @GetMapping("/budgets")
    public Map<String,Object> budget(@RequestParam String month) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT * FROM monthly_budgets WHERE month_key=?", month);
        if (!rows.isEmpty()) { rows.get(0).put("configured", true); return rows.get(0); }
        return Map.of("month_key", month, "amount", 0, "configured", false, "copied_from_previous", false);
    }

    @PutMapping("/budgets")
    public Map<String,Object> updateBudget(@RequestBody Map<String,Object> b) {
        String month = b.get("month").toString(); BigDecimal amount = new BigDecimal(b.get("amount").toString()); boolean copy = Boolean.parseBoolean(b.getOrDefault("copyNext", false).toString());
        jdbc.update("INSERT INTO monthly_budgets(month_key, amount, copied_from_previous) VALUES (?,?,?) ON CONFLICT (month_key) DO UPDATE SET amount=EXCLUDED.amount, copied_from_previous=EXCLUDED.copied_from_previous", month, amount, copy);
        if (copy) {
            LocalDate next = LocalDate.parse(month + "-01").plusMonths(1);
            jdbc.update("INSERT INTO monthly_budgets(month_key, amount, copied_from_previous) VALUES (?,?,TRUE) ON CONFLICT (month_key) DO UPDATE SET amount=EXCLUDED.amount, copied_from_previous=TRUE", next.toString().substring(0, 7), amount);
        }
        return Map.of("ok", true);
    }

    private BigDecimal amount(String sql, Object... args) { BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, args); return v == null ? BigDecimal.ZERO : v; }
    private String dateString(Object value) { return value == null ? null : value.toString().substring(0, Math.min(10, value.toString().length())); }
    private java.sql.Date dateOrNull(Object value) { return value == null || value.toString().isBlank() ? null : java.sql.Date.valueOf(value.toString()); }
}
