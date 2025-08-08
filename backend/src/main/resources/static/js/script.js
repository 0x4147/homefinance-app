    let currentChartType = 'category';
    let chartInstance = null;
    let chartData = {};

    function showPage(page) {
        document.getElementById('dashboardContainer').style.display = 'none';
        document.getElementById('transactionContainer').style.display = 'none';
        document.getElementById('paymentsContainer').style.display = 'none';
        document.getElementById('batchUploadContainer').style.display = 'none';

        document.getElementById(page).style.display = 'block';
    }

    document.addEventListener("DOMContentLoaded", function () {
        const form = document.getElementById("transaction-entry-form");

        // Handle Submit (POST)
        document.getElementById("submit-btn").addEventListener("click", function (e) {
            e.preventDefault();

            const data = {
                transactionType: document.getElementById("entry-type").value,
                date: document.getElementById("entry-date").value,
                amount: document.getElementById("entry-amount").value,
                account: document.getElementById("entry-account").value,
                entity: document.getElementById("entry-entity").value,
                category: document.getElementById("entry-category").value,
                details: document.getElementById("entry-description").value,
                person: document.getElementById("entry-person").value,
            };
            console.log("Payload JSON string:", JSON.stringify(data, null, 2));
            fetch("/api/v1/transaction/saveTransaction", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(data)
            })
                .then(response => {
                    if (!response.ok) {
                        // Handle error response
                        return response.json().then(err => { throw err });
                    }
                    return response.json();
                })
                .then(data => {
                    alert("Saved successfully!");
                    form.reset();
                })
                .catch(error => {
                    console.error("Error saving data:", error);  // This will show proper error object
                    alert(error.error || "Failed to save data.");  // Show backend message if exists
                });
        });

        // Handle Filter
        document.getElementById("filter-btn").addEventListener("click", function (e) {
            e.preventDefault();

            const params = new URLSearchParams({
                start: document.getElementById("show-start-date").value,
                end: document.getElementById("show-end-date").value,
            });

            console.log("Query Params I'm sending:", params.toString());

            fetch("/api/v1/transaction/getTransactionsByDateRange?" + params.toString())
                .then(response => response.json())
                .then(data => {
                    const tableBody = document.querySelector("#results-table tbody");
                    tableBody.innerHTML = ""; // clear previous results

                    if (data.length === 0) {
                        tableBody.innerHTML = `<tr><td colspan="5" class="text-center">No records found</td></tr>`;
                        return;
                    }
                    console.log("Data from backend:", data);
                    console.log("Data from backend:", JSON.stringify(data, null, 2));
                    data.forEach(item => {
                        const row = `
                                        <tr>
                                            <td>${item.date}</td>
                                            <td>${item.transactionType}</td>
                                            <td>${item.amount}</td>
                                            <td>${item.account}</td>
                                            <td>${item.category}</td>
                                        </tr>
                                    `;
                        tableBody.insertAdjacentHTML("beforeend", row);
                    });
                })
                .catch(error => {
                    console.error("Error fetching data:", error);
                    alert("Failed to fetch data.");
                });
        });

        // Handle Get all transactions
        document.getElementById("get-all-transactions-btn").addEventListener("click", function (e) {
            e.preventDefault();

            fetch("/api/v1/transaction/getAllTransactions")
                .then(response => response.json())
                .then(data => {
                    console.log(data);  // Log the data to inspect its structure
                    const tableBody = document.querySelector("#results-table tbody");
                    tableBody.innerHTML = ""; // clear previous results

                    if (data.length === 0) {
                        tableBody.innerHTML = `<tr><td colspan="5" class="text-center">No records found</td></tr>`;
                        return;
                    }

                    data.forEach(item => {
                        const row = `
                                        <tr>
                                            <td>${item.type}</td>
                                            <td>${item.date}</td>
                                            <td>${item.amount}</td>
                                            <td>${item.name}</td>
                                            <td>${item.category}</td>
                                        </tr>
                                    `;
                        tableBody.insertAdjacentHTML("beforeend", row);
                    });
                })
                .catch(error => {
                    console.error("Error fetching data:", error);
                    alert("Failed to fetch data.");
                });
        });

    });

    function showChart(tab, type) {
        currentChartType = type;
        document.querySelectorAll('#chartTabs .nav-link').forEach(btn => btn.classList.remove('active'));
        tab.classList.add('active');

        document.getElementById('chartTitle').innerText = {
            category: 'Spending by Category',
            merchant: 'Top Merchants',
            month: 'Spending by Month'
        }[type];

        // Toggle input sets
        document.getElementById('dateRangeInputs').style.display = (type === 'month') ? 'none' : 'flex';
        document.getElementById('monthYearRangeInputs').style.display = (type === 'month') ? 'flex' : 'none';

        document.getElementById('transactionDetails').style.display = 'none';
        if (chartInstance) chartInstance.destroy();
        chartInstance = null;
    }

    async function loadChartData() {
        const urlMap = {
            category: '/api/v1/transaction/getExpensesByCategory',
            merchant: '/api/v1/transaction/getExpensesByEntity',
            month: '/api/v1/transaction/getExpensesByMonth'
        };

        let params = {};
        if (currentChartType === 'month') {
            params = {
                startMonth: document.getElementById('startMonth').value,
                endMonth: document.getElementById('endMonth').value
            };
        } else {
            params = {
                startDate: document.getElementById('startDate').value,
                endDate: document.getElementById('endDate').value
            };
        }

        const { data } = await axios.get(urlMap[currentChartType], { params });
        chartData = data;

        renderChart(data);
    }

    function renderChart(data) {
        const labels = Object.keys(data.totals);
        const values = Object.values(data.totals);

        const config = {
            type: currentChartType === 'merchant' ? 'pie' : 'bar',
            data: {
                labels,
                datasets: [{
                    label: 'Amount',
                    data: values,
                    backgroundColor: ['#0d6efd', '#6610f2', '#6f42c1', '#198754', '#dc3545', '#ffc107']
                }]
            },
            options: {
                onClick: (evt, elements) => {
                    if (elements.length > 0) {
                        const index = elements[0].index;
                        const key = labels[index];
                        renderTransactions(key);
                    }
                }
            }
        };

        const ctx = document.getElementById('chartCanvas').getContext('2d');
        if (chartInstance) chartInstance.destroy();
        chartInstance = new Chart(ctx, config);
    }

    function renderTransactions(key) {
        const tbody = document.getElementById('transactionTableBody');
        tbody.innerHTML = '';

        const rows = chartData.details[key] || [];
        for (const tx of rows) {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${tx.date}</td>
                <td>${tx.entity}</td>
                <td>$${parseFloat(tx.amount).toFixed(2)}</td>
                <td>${tx.category.name}</td>
                <td>${tx.account || ''}</td>
            `;
            tbody.appendChild(tr);
        }

        document.getElementById('transactionDetails').style.display = 'block';
    }


    function fetchBalance() {
        const month = document.getElementById("monthSelect").value;
        const year = document.getElementById("yearSelect").value;

        if (!month || !year) {
            alert("Please select both month and year.");
            return;
        }

        // Replace with your actual API endpoint
        const url = `/api/v1/transaction/getMonthlyBalance?month=${month}&year=${year}`;

        fetch(url)
            .then(response => response.json())
            .then(data => {
                const { asankaPaid, divyaPaid, balanceAmount, whoOwes, monthAndYear } = data;

                document.getElementById("asankaPaid").textContent =
                    `Asanka has paid $${asankaPaid} in ${months[month - 1]}, ${year}.`;
                document.getElementById("divyaPaid").textContent =
                    `Divya has paid $${divyaPaid} in ${months[month - 1]}, ${year}.`;

                const balanceText = balanceAmount === 0
                    ? `Both are settled. Balance is $0.`
                    : `${whoOwes} owes $${balanceAmount} to ${whoOwes === 'Asanka' ? 'Divya' : 'Asanka'}`;

                document.getElementById("balanceResult").textContent = balanceText;
                document.getElementById("resultSection").classList.remove("d-none");
            })
            .catch(error => {
                alert("Error fetching balance data.");
                console.error(error);
            });
    }

    function flagAsBalanced() {
        const month = document.getElementById("monthSelect").value;
        const year = document.getElementById("yearSelect").value;

        // Replace with your actual POST endpoint
        const url = `/api/balance/flag`;
        const payload = { month, year };

        fetch(url, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(payload)
        })
            .then(res => {
                if (res.ok) {
                    alert("Balance flagged as settled.");
                } else {
                    throw new Error("Failed to flag as balanced.");
                }
            })
            .catch(err => {
                alert("Error flagging as balanced.");
                console.error(err);
            });
    }

    document.getElementById('uploadButton').addEventListener('click', function () {

        const fileInput = document.getElementById('csvFile');
        const sourceType = document.getElementById('sourceTypeSelect').value;

        const file = fileInput.files[0];

        const statusDiv = document.getElementById('statusMessage');
        statusDiv.innerHTML = '';

        if (!file) {
            statusDiv.innerHTML = '<div class="alert alert-warning">Please select a CSV file.</div>';
            return;
        }

        const formData = new FormData();
        formData.append('file', file);
        formData.append('sourceType', sourceType);

        fetch('/api/v1/transactionBatchUpload', {
            method: 'POST',
            body: formData
        })
            .then(response => {
                if (response.ok) {
                    return response.text();
                } else {
                    throw new Error('Upload failed');
                }
            })
            .then(result => {
                statusDiv.innerHTML = `<div class="alert alert-success">Upload successful: ${result}</div>`;
            })
            .catch(error => {
                statusDiv.innerHTML = `<div class="alert alert-danger">Error: ${error.message}</div>`;
            });
    });

    document.getElementById('sourceTypeSelect').addEventListener('change', function() {
        const fileInput = document.getElementById('csvFile');
        if (this.value) {
            fileInput.disabled = false;
        } else {
            fileInput.disabled = true;
        }
    });
