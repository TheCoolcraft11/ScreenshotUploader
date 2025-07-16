       document.addEventListener('DOMContentLoaded', function() {
            const isDarkMode = localStorage.getItem('darkMode') === 'enabled';
            if (isDarkMode) {
                document.documentElement.classList.add('dark-mode');
                document.body.classList.add('dark-mode');
            }

            loadUrlHistory();

            document.getElementById('urlInput').addEventListener('input', function() {
                const urlInput = this.value.trim();
                const imagePreview = document.getElementById('imagePreview');
                imagePreview.innerHTML = '';
                const imageExtensions = ['.png', '.jpg', '.jpeg', '.gif', '.webp', '.bmp', '.svg'];
                if (imageExtensions.some(ext => urlInput.toLowerCase().endsWith(ext))) {
                    imagePreview.innerHTML = `<img src="${urlInput}" alt="Image preview" style="max-width:100%;border-radius:8px;box-shadow:0 2px 8px rgba(0,0,0,0.1);">`;
                }
            });

            document.getElementById('toggleDarkMode').addEventListener('click', function() {
                document.documentElement.classList.toggle('dark-mode');
                document.body.classList.toggle('dark-mode');

                const isDarkMode = document.body.classList.contains('dark-mode');
                localStorage.setItem('darkMode', isDarkMode ? 'enabled' : 'disabled');
            });

            document.getElementById('shortenForm').addEventListener('submit', function(e) {
                e.preventDefault();
                shortenUrl();
            });

            document.getElementById('copyButton').addEventListener('click', function() {
                copyShortUrl();
            });

            document.getElementById('toggleHistory').addEventListener('click', function() {
                const historyItems = document.getElementById('historyItems');
                const toggleBtn = document.getElementById('toggleHistory');

                if (historyItems.style.display === 'none') {
                    historyItems.style.display = 'block';
                    toggleBtn.textContent = 'Hide History';
                } else {
                    historyItems.style.display = 'none';
                    toggleBtn.textContent = 'Show History';
                }
            });
        });

        function shortenUrl() {
            const urlInput = document.getElementById('urlInput').value.trim();
            const passphraseInput = document.getElementById('passphraseInput').value;
            const resultContainer = document.getElementById('resultContainer');
            const shortUrlElement = document.getElementById('shortUrl');
            const errorMessage = document.getElementById('errorMessage');

            if (!urlInput) {
                showError("Please enter a URL to shorten");
                return;
            }


            try {
                new URL(urlInput);
            } catch (e) {
                showError("Please enter a valid URL (including http:// or https://)");
                return;
            }

            resultContainer.style.display = 'none';
            errorMessage.style.display = 'none';

            fetch('/shorten', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Shortener-Passphrase': passphraseInput
                },
                body: JSON.stringify({ url: urlInput })
            })
            .then(response => {
                if (!response.ok) {
                    if (response.status === 403) {
                        throw new Error('Invalid passphrase');
                    } else if (response.status === 423) {
                        throw new Error('URL shortening is currently disabled');
                    } else {
                        throw new Error('Failed to shorten URL');
                    }
                }
                return response.json();
            })
            .then(data => {
                shortUrlElement.textContent = data.shortUrl;
                shortUrlElement.href = data.shortUrl;
                resultContainer.style.display = 'block';


                saveToHistory(urlInput, data.shortUrl);
                loadUrlHistory();
            })
            .catch(error => {
                showError(error.message);
            });
        }

        function copyShortUrl() {
            const shortUrl = document.getElementById('shortUrl').textContent;
            navigator.clipboard.writeText(shortUrl)
                .then(() => {
                    const copyButton = document.getElementById('copyButton');
                    const originalText = copyButton.textContent;
                    copyButton.textContent = 'Copied!';
                    setTimeout(() => {
                        copyButton.textContent = originalText;
                    }, 2000);
                })
                .catch(err => {
                    showError('Failed to copy: ' + err);
                });
        }

        function showError(message) {
            const errorMessage = document.getElementById('errorMessage');
            errorMessage.textContent = message;
            errorMessage.style.display = 'block';
        }

        function saveToHistory(originalUrl, shortUrl) {
            let history = JSON.parse(localStorage.getItem('urlShortenerHistory') || '[]');


            history.unshift({
                original: originalUrl,
                short: shortUrl,
                timestamp: new Date().toISOString()
            });

            if (history.length > 10) {
                history = history.slice(0, 10);
            }

            localStorage.setItem('urlShortenerHistory', JSON.stringify(history));
        }

        function loadUrlHistory() {
            const history = JSON.parse(localStorage.getItem('urlShortenerHistory') || '[]');
            const historyContainer = document.getElementById('historyItems');

            if (history.length === 0) {
                historyContainer.innerHTML = '<p>No history yet</p>';
                return;
            }

            historyContainer.innerHTML = '';

            history.forEach(item => {
                const historyItem = document.createElement('div');
                historyItem.className = 'history-item';

                const originalUrl = document.createElement('div');
                originalUrl.className = 'history-original';
                originalUrl.title = item.original;
                originalUrl.textContent = item.original;

                const shortUrl = document.createElement('a');
                shortUrl.className = 'history-short';
                shortUrl.href = item.short;
                shortUrl.textContent = item.short.split('/').pop();
                shortUrl.target = '_blank';

                historyItem.appendChild(originalUrl);
                historyItem.appendChild(shortUrl);

                historyContainer.appendChild(historyItem);
            });
        }

