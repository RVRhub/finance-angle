(() => {
    "use strict";

    const eur = new Intl.NumberFormat("de-DE", {
        style: "currency",
        currency: "EUR",
        maximumFractionDigits: 2
    });
    const palette = ["#2563eb", "#16a34a", "#f59e0b", "#dc2626", "#7c3aed", "#0891b2", "#db2777", "#65a30d"];

    echarts.registerTheme("finance-angle", {
        color: palette,
        textStyle: { fontFamily: "Inter, ui-sans-serif, system-ui, sans-serif", color: "#334155" },
        title: { textStyle: { color: "#0f172a" } },
        legend: { textStyle: { color: "#475569" } },
        categoryAxis: {
            axisLine: { lineStyle: { color: "#cbd5e1" } },
            axisLabel: { color: "#64748b" }
        },
        valueAxis: {
            axisLine: { show: false },
            axisLabel: { color: "#64748b" },
            splitLine: { lineStyle: { color: "#e2e8f0" } }
        }
    });

    const charts = [];
    const amount = money => Number(money?.amount ?? 0);
    const signedSnapshotAmount = snapshot =>
        amount(snapshot.balance) * (snapshot.type === "CREDIT" || snapshot.type === "LOAN" ? -1 : 1);
    const formatMoney = value => eur.format(Number(value ?? 0));
    const axisMoney = value => {
        const absolute = Math.abs(value);
        if (absolute >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M €`;
        if (absolute >= 1_000) return `${(value / 1_000).toFixed(0)}k €`;
        return `${Number(value).toFixed(0)} €`;
    };

    async function fetchJson(url) {
        const response = await fetch(url);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json();
    }

    function createChart(elementId) {
        const element = document.getElementById(elementId);
        const chart = echarts.init(element, "finance-angle", { renderer: "canvas" });
        chart.showLoading("default", { text: "Loading…" });
        charts.push(chart);
        return chart;
    }

    function showMessage(chart, message) {
        chart.hideLoading();
        chart.clear();
        chart.getDom().innerHTML = `<div class="chart-message">${escapeHtml(message)}</div>`;
    }

    function escapeHtml(value) {
        const node = document.createElement("div");
        node.textContent = value;
        return node.innerHTML;
    }

    const commonOption = {
        animationDuration: 350,
        grid: { left: 16, right: 18, top: 46, bottom: 64, containLabel: true },
        toolbox: {
            right: 0,
            feature: {
                saveAsImage: { title: "Save image", pixelRatio: 2 },
                restore: { title: "Reset" }
            }
        },
        tooltip: {
            trigger: "axis",
            valueFormatter: formatMoney
        },
        dataZoom: [
            { type: "inside", filterMode: "none" },
            { type: "slider", height: 20, bottom: 12, filterMode: "none" }
        ],
        yAxis: {
            type: "value",
            axisLabel: { formatter: axisMoney }
        }
    };

    function metric(label, money, change, lowerIsBetter = false) {
        const changeAmount = change ? amount(change) : null;
        const improved = changeAmount === null || (lowerIsBetter ? changeAmount <= 0 : changeAmount >= 0);
        const delta = changeAmount === null
            ? "—"
            : `<span class="${improved ? "positive" : "negative"}">${changeAmount > 0 ? "+" : ""}${formatMoney(changeAmount)}</span>`;
        return `
            <div class="metric">
                <div class="metric-label">${escapeHtml(label)}</div>
                <div class="metric-value">${formatMoney(amount(money))}</div>
                <div class="note">vs previous month: ${delta}</div>
            </div>`;
    }

    async function loadMonthlyPosition() {
        const chart = createChart("monthly-position-chart");
        try {
            const rows = await fetchJson("/api/account-positions/comparison");
            const body = document.getElementById("position-rows");
            const latest = document.getElementById("latest-position");

            if (!rows.length) {
                body.innerHTML = '<tr><td colspan="6">No monthly positions yet. Create one in data management.</td></tr>';
                showMessage(chart, "No monthly positions yet");
                return;
            }

            const newest = rows.at(-1);
            latest.innerHTML = [
                metric("Assets", newest.assets, newest.change.assets),
                metric("Debts", newest.debts, newest.change.debts, true),
                metric("Savings", newest.savings, newest.change.savings),
                metric("Net position", newest.netPosition, newest.change.netPosition)
            ].join("");

            body.innerHTML = [...rows].reverse().map(row => {
                const change = row.change.netPosition ? amount(row.change.netPosition) : null;
                const changeHtml = change === null
                    ? "—"
                    : `<span class="${change >= 0 ? "positive" : "negative"}">${change > 0 ? "+" : ""}${formatMoney(change)}</span>`;
                return `
                    <tr>
                        <td>${escapeHtml(row.month)}</td>
                        <td>${formatMoney(amount(row.assets))}</td>
                        <td>${formatMoney(amount(row.debts))}</td>
                        <td>${formatMoney(amount(row.savings))}</td>
                        <td>${formatMoney(amount(row.netPosition))}</td>
                        <td>${changeHtml}</td>
                    </tr>`;
            }).join("");

            chart.hideLoading();
            chart.setOption({
                ...commonOption,
                legend: { data: ["Assets", "Debts", "Savings", "Net position"] },
                xAxis: { type: "category", data: rows.map(row => row.month), boundaryGap: true },
                series: [
                    {
                        name: "Assets",
                        type: "bar",
                        stack: "position",
                        emphasis: { focus: "series" },
                        data: rows.map(row => amount(row.assets))
                    },
                    {
                        name: "Debts",
                        type: "bar",
                        stack: "position",
                        emphasis: { focus: "series" },
                        data: rows.map(row => -amount(row.debts)),
                        itemStyle: { color: "#dc2626" }
                    },
                    {
                        name: "Savings",
                        type: "bar",
                        stack: "position",
                        emphasis: { focus: "series" },
                        data: rows.map(row => amount(row.savings))
                    },
                    {
                        name: "Net position",
                        type: "line",
                        smooth: true,
                        symbolSize: 7,
                        lineStyle: { width: 3 },
                        data: rows.map(row => amount(row.netPosition)),
                        markLine: { silent: true, symbol: "none", data: [{ yAxis: 0 }] }
                    }
                ]
            });
        } catch (error) {
            document.getElementById("position-rows").innerHTML =
                `<tr><td colspan="6">Could not load monthly positions: ${escapeHtml(error.message)}</td></tr>`;
            showMessage(chart, `Could not load monthly positions: ${error.message}`);
        }
    }

    async function loadSpending() {
        const chart = createChart("spending-chart");
        try {
            const rows = await fetchJson("/api/summary/spending?months=12");
            if (!rows.length) {
                showMessage(chart, "No spending data yet");
                return;
            }

            const months = [...new Set(rows.map(row => row.month))].sort();
            const categories = [...new Set(rows.map(row => row.category || "Uncategorised"))].sort();
            const totals = new Map(rows.map(row => [`${row.month}::${row.category || "Uncategorised"}`, Number(row.total)]));

            chart.hideLoading();
            chart.setOption({
                ...commonOption,
                legend: { type: "scroll", top: 0, left: 0, right: 90 },
                xAxis: { type: "category", data: months },
                series: categories.map(category => ({
                    name: category,
                    type: "bar",
                    stack: "spending",
                    emphasis: { focus: "series" },
                    data: months.map(month => totals.get(`${month}::${category}`) ?? 0)
                }))
            });
        } catch (error) {
            showMessage(chart, `Could not load spending: ${error.message}`);
        }
    }

    function groupSnapshots(snapshots) {
        return snapshots.reduce((dates, snapshot) => {
            const date = snapshot.date;
            dates.set(date, (dates.get(date) ?? 0) + signedSnapshotAmount(snapshot));
            return dates;
        }, new Map());
    }

    async function loadBalances() {
        const balanceChart = createChart("balance-chart");
        const accountChart = createChart("account-chart");
        try {
            const snapshots = await fetchJson("/api/snapshots");
            if (!snapshots.length) {
                showMessage(balanceChart, "No balance snapshots yet");
                showMessage(accountChart, "No balance snapshots yet");
                return;
            }

            const netByDate = [...groupSnapshots(snapshots).entries()].sort(([a], [b]) => a.localeCompare(b));
            balanceChart.hideLoading();
            balanceChart.setOption({
                ...commonOption,
                xAxis: { type: "category", boundaryGap: false, data: netByDate.map(([date]) => date) },
                series: [{
                    name: "Net position",
                    type: "line",
                    smooth: true,
                    symbolSize: 7,
                    lineStyle: { width: 3 },
                    areaStyle: { opacity: 0.12 },
                    data: netByDate.map(([, value]) => value),
                    markLine: { silent: true, symbol: "none", data: [{ yAxis: 0 }] }
                }]
            });

            const dates = [...new Set(snapshots.map(snapshot => snapshot.date))].sort();
            const accountNames = [...new Set(snapshots.map(snapshot =>
                `${snapshot.account || "Unassigned"} (${snapshot.type.toLowerCase()})`
            ))].sort();
            const values = new Map(snapshots.map(snapshot => [
                `${snapshot.date}::${snapshot.account || "Unassigned"} (${snapshot.type.toLowerCase()})`,
                signedSnapshotAmount(snapshot)
            ]));

            accountChart.hideLoading();
            accountChart.setOption({
                ...commonOption,
                legend: { type: "scroll", top: 0, left: 0, right: 90 },
                xAxis: { type: "category", boundaryGap: false, data: dates },
                series: accountNames.map(name => ({
                    name,
                    type: "line",
                    connectNulls: true,
                    showSymbol: dates.length < 24,
                    emphasis: { focus: "series" },
                    data: dates.map(date => values.get(`${date}::${name}`) ?? null)
                }))
            });
        } catch (error) {
            showMessage(balanceChart, `Could not load balances: ${error.message}`);
            showMessage(accountChart, `Could not load account balances: ${error.message}`);
        }
    }

    window.addEventListener("resize", () => charts.forEach(chart => chart.resize()));
    Promise.allSettled([loadMonthlyPosition(), loadSpending(), loadBalances()]);
})();
