function showModal(src) {
            const modal = document.getElementById('modal');
            const modalImg = document.getElementById('modalImage');
            const loading = document.getElementById('loading');
            const likeButton = document.getElementById('likeButton');

            modal.style.display = 'flex';
            currentSrc = src;
            currentFilename = src.split('/').pop();
            currentIndex = images.indexOf(src) + 1;
            updateImageCount(images.length);
            loadImage(src);

            if (likeButton) {
                likeButton.classList.toggle('liked', likedImages.includes(src));
            }

            loading.style.display = 'block';
            modalImg.onload = () => {
                loading.style.display = 'none';
                modalImg.classList.add('visible');
            };

            stopSlideshow();
            document.addEventListener('keydown', keyNavigation);
            fetchComments(src);
            displayImageMetadata(src);
            modal.focus();
        }

        function loadImage(src) {
            const modalImg = document.getElementById('modalImage');
            modalImg.src = src;
            modalImg.classList.remove('visible');
            const likeButton = document.getElementById('likeButton');
            if (likeButton) {
                const normalizedSrc = modalImg.src.replace(window.location.origin, '');
                const isLiked = likedImages.includes(modalImg.src) || likedImages.includes(normalizedSrc);
                likeButton.classList.toggle('liked', isLiked);
            }
        }

        function hideModal(event) {
            const modal = document.getElementById('modal');

            if (event.target === modal || event.target.tagName === 'SPAN') {
                modal.style.display = 'none';
                document.getElementById('modalImage').src = '';
                clearInterval(intervalId);
                slideshowActive = false;
                document.removeEventListener('keydown', keyNavigation);
            }
        }

        function keyNavigation(e) {
            if (e.key === 'ArrowRight') {
                nextImage();
            } else if (e.key === 'ArrowLeft') {
                previousImage();
            }else if (e.key === 'Escape') {
               const modal = document.getElementById('modal');
                  if (modal.style.display === 'flex') {
                     modal.style.display = 'none';
                     document.getElementById('modalImage').src = '';
                     clearInterval(intervalId);
                     slideshowActive = false;
                     document.removeEventListener('keydown', keyNavigation);
                 }
            }
        }

        function addSwipeSupport() {
            const modalContent = document.getElementById('modal-content-wrapper');
            let startX, startY;

            modalContent.addEventListener('touchstart', (e) => {
                startX = e.touches[0].clientX;
                startY = e.touches[0].clientY;
            });

            modalContent.addEventListener('touchend', (e) => {
                const diffX = startX - e.changedTouches[0].clientX;
                const diffY = startY - e.changedTouches[0].clientY;

                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 50) {
                    if (diffX > 0) {
                        nextImage();
                    } else {
                        previousImage();
                    }
                }
            });
        }

        document.addEventListener('DOMContentLoaded', addSwipeSupport);

        function previousImage() {
            currentIndex = (currentIndex - 1 + images.length) % images.length;
            loadImage(images[currentIndex]);
            updateImageCount(images.length);
            fetchComments(images[currentIndex]);
            displayImageMetadata(images[currentIndex]);
        }

        function nextImage() {
            currentIndex = (currentIndex + 1) % images.length;
            loadImage(images[currentIndex]);
            updateImageCount(images.length);
            fetchComments(images[currentIndex]);
            displayImageMetadata(images[currentIndex]);
        }

        function updateImageCount(total) {
            const imageCount = document.getElementById('imageCount');
            imageCount.textContent = `Image ${currentIndex + 1} of ${total}`;
        }

        function toggleSlideshow() {
            if (slideshowActive) {
                stopSlideshow();
            } else {
                startSlideshow();
            }
        }

        function startSlideshow() {
            slideshowActive = true;
            const interval = document.getElementById('intervalInput').value * 1000 || 5000;
            intervalId = setInterval(nextImage, interval);
            document.getElementById('slideshowToggle').textContent = 'Stop Slideshow';
        }

        function stopSlideshow() {
            slideshowActive = false;
            clearInterval(intervalId);
            document.getElementById('slideshowToggle').textContent = 'Start Slideshow';
        }

        function randomImage() {
            const randomIndex = Math.floor(Math.random() * images.length);
            loadImage(images[randomIndex]);
            updateImageCount(images.length);
            fetchComments(images[randomIndex]);
            displayImageMetadata(images[randomIndex]);
        }

        function toggleFullScreen() {
            const modal = document.getElementById('modal');
            const modalImg = document.getElementById('modalImage');
            if (modalImg.requestFullscreen) {
                modalImg.requestFullscreen();
            } else if (modalImg.webkitRequestFullscreen) {
                modalImg.webkitRequestFullscreen();
            }
        }

        function downloadImage() {
            const a = document.createElement('a');
            a.href = currentSrc;
            a.download = currentSrc.split('/').pop();
            a.click();
        }


function displayImageMetadata(src) {
    const metadataContainer = document.getElementById('screenshotMetadata');
    metadataContainer.innerHTML = '';

    const basicData = imageMetadata[src];
    if (!basicData) {
        metadataContainer.innerHTML = '<p>No metadata available</p>';
        return;
    }

    const username = basicData.username;
    const filename = basicData.filename;
    const timestamp = new Date(basicData.timestamp).toLocaleString();

    let html = `
        <div class="metadata-item">
            <strong>Filename:</strong> ${filename}
        </div>
        <div class="metadata-item">
            <strong>Username:</strong> ${username}
        </div>
        <div class="metadata-item">
            <strong>Date:</strong> ${timestamp}
        </div>
    `;

    if (basicData.hasMetadata && basicData.metadata) {
        const metadata = basicData.metadata;

        if (metadata.coordinates) {
            html += `<div class="metadata-item"><strong>Coordinates:</strong> ${metadata.coordinates}</div>`;
        }
        if (metadata.dimension) {
            html += `<div class="metadata-item"><strong>Dimension:</strong> ${metadata.dimension}</div>`;
        }
        if (metadata.biome) {
            html += `<div class="metadata-item"><strong>Biome:</strong> ${metadata.biome}</div>`;
        }
        if (metadata.system_info) {
            html += `<div class="metadata-item"><strong>System:</strong> ${metadata.system_info}</div>`;
        }
        if (metadata.world_info) {
            html += `<div class="metadata-item"><strong>World Info:</strong> ${metadata.world_info}</div>`;
        }
        if (metadata.client_settings) {
            html += `<div class="metadata-item"><strong>Settings:</strong> ${metadata.client_settings}</div>`;
        }
    }

    metadataContainer.innerHTML = html;
}

function deleteImage() {
    const imageName = currentSrc.split('/').pop();
    let passphrase = "";

    const passphraseElement = document.getElementById('deletePassphrase');
    if (passphraseElement) {
        passphrase = passphraseElement.value;
        if (!passphrase && !allowEmptyPassphrase) {
            alert("Please enter the deletion passphrase");
            return;
        }
    }

    if (confirm(`Are you sure you want to delete the image "${imageName}"?`)) {
        fetch(`delete/${imageName}`, {
            method: 'DELETE',
            headers: {
                'Content-Type': 'application/json',
                'X-Delete-Passphrase': passphrase
            },
        })
        .then((response) => {
            if (response.status === 401) {
                alert("Incorrect passphrase!");
            } else if (response.ok) {
                alert("Image deleted successfully!");
                document.getElementById('modal').style.display = 'none';
                location.reload();
            } else {
                alert("Failed to delete image!");
            }
        })
        .catch(error => {
            console.error('Error:', error);
            alert('An error occurred while deleting the image.');
        });
    }
}
       function toggleDarkMode() {
         const body = document.body;
            const header = document.querySelector('h1');


             body.classList.toggle('dark-mode');
            header.classList.toggle('dark-mode');

            if (body.classList.contains('dark-mode')) {
                localStorage.setItem('darkMode', 'enabled');
            } else {
                localStorage.setItem('darkMode', 'disabled');
            }
        }


        window.onload = () => {
            const darkMode = localStorage.getItem('darkMode');

            if (darkMode === 'enabled') {
             document.body.classList.add('dark-mode');
             document.querySelector('h1').classList.add('dark-mode');
            }

            loadLikedImages();
        };

        function fetchComments(imageSrc) {
            const filename = imageSrc.split('/').pop();
            fetch(`/comments/${filename}`)
                .then(response => response.json())
                .then(comments => {
                    const commentsContainer = document.getElementById('commentsContainer');
                    commentsContainer.innerHTML = '<h3>Comments</h3>';
                    comments.forEach(comment => {
                        const commentDiv = document.createElement('div');
                        commentDiv.classList.add('comment');
                        commentDiv.textContent = `${comment.author}: ${comment.comment}`;
                        commentsContainer.appendChild(commentDiv);
                    });
                })
                .catch(error => console.error('Error fetching comments:', error));
        }

        function submitComment() {
            const commentText = document.getElementById('newComment').value;
            const commentAuthor = document.getElementById('commentAuthor').value || 'Anonymous';
            if (!commentText) return alert('Comment cannot be empty');

            const filename = currentSrc.split('/').pop();

            const commentData = {
                comment: commentText,
                author: commentAuthor,
                timestamp: new Date().toISOString()
            };

            fetch(`/comment/${filename}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(commentData)
            })
                .then(response => {
                    if (response.ok) {
                        fetchComments(currentSrc);
                        document.getElementById('newComment').value = '';
                        document.getElementById('commentAuthor').value = '';
                    } else {
                        alert('Failed to submit comment');
                    }
                })
                .catch(error => console.error('Error submitting comment:', error));
        }

let likedImages = [];

function loadLikedImages() {
    const storedLikes = localStorage.getItem('likedScreenshots');
    if (storedLikes) {
        likedImages = JSON.parse(storedLikes);
        updateLikeButtons();
        sortGalleryItems();
    }
}

function saveLikedImages() {
    localStorage.setItem('likedScreenshots', JSON.stringify(likedImages));
}

function toggleLike(imageSrc, event) {
    if (event) event.stopPropagation();

    const index = likedImages.indexOf(imageSrc);
    if (index !== -1) {
        likedImages.splice(index, 1);
    } else {
        likedImages.push(imageSrc);
    }

    saveLikedImages();
    updateLikeButtons();

    if (currentSrc === imageSrc) {
        const likeButton = document.getElementById('likeButton');
        if (likeButton) {
            likeButton.classList.toggle('liked', likedImages.includes(imageSrc));
        }
    }

    sortGalleryItems();
}

function updateLikeButtons() {
    document.querySelectorAll('.gallery-item').forEach(item => {
        const imgSrc = item.querySelector('img').src;
        const likeButton = item.querySelector('.like-button');

        if (likeButton) {
            const normalizedSrc = imgSrc.replace(window.location.origin, '');
            const isLiked = likedImages.includes(imgSrc) || likedImages.includes(normalizedSrc);
            likeButton.classList.toggle('liked', isLiked);
        }
    });

    if (currentSrc) {
        const modalLikeButton = document.getElementById('likeButton');
        if (modalLikeButton) {
            modalLikeButton.classList.toggle('liked', likedImages.includes(currentSrc));
        }
    }
}

function sortGalleryItems() {
    const gallery = document.querySelector('.gallery');
    if (!gallery) return;

    const items = Array.from(gallery.children);

    items.sort((a, b) => {
        const imgA = a.querySelector('img').src.replace(window.location.origin, '');
        const imgB = b.querySelector('img').src.replace(window.location.origin, '');

        const isLikedA = likedImages.includes(imgA) ? 1 : 0;
        const isLikedB = likedImages.includes(imgB) ? 1 : 0;

        return isLikedB - isLikedA;
    });

    gallery.innerHTML = '';
    items.forEach(item => gallery.appendChild(item));
}

function toggleAdvancedSearch() {
    const advancedOptions = document.getElementById('advancedSearchOptions');
    const toggleButton = document.getElementById('advancedSearchToggle');

    if (advancedOptions.style.display === 'none' || !advancedOptions.style.display) {
        advancedOptions.style.display = 'block';
        toggleButton.textContent = 'Hide Advanced Search';
    } else {
        advancedOptions.style.display = 'none';
        toggleButton.textContent = 'Advanced Search';
    }
}

function toggleSearchInfo() {
    const infoBox = document.getElementById('searchInfoBox');
    if (infoBox.style.display === 'none' || !infoBox.style.display) {
        infoBox.style.display = 'block';
    } else {
        infoBox.style.display = 'none';
    }
}

function searchGallery() {
    const searchTerm = document.getElementById('searchInput').value.toLowerCase();
    const filterUsername = document.getElementById('filterUsername').checked;
    const filterFilename = document.getElementById('filterFilename').checked;
    const filterTags = document.getElementById('filterTags').checked;
    const filterMetadata = document.getElementById('filterMetadata').checked;

    const filterCoordinates = document.getElementById('filterCoordinates')?.checked || false;
    const filterDimension = document.getElementById('filterDimension')?.checked || false;
    const filterBiome = document.getElementById('filterBiome')?.checked || false;
    const filterSystemInfo = document.getElementById('filterSystemInfo')?.checked || false;
    const filterWorldInfo = document.getElementById('filterWorldInfo')?.checked || false;
    const filterClientSettings = document.getElementById('filterClientSettings')?.checked || false;

    const searchTerms = searchTerm.split(/,(?=(?:[^"]*"[^"]*")*[^"]*$)/).map(term => term.trim()).filter(term => term);

    const galleryItems = document.querySelectorAll('.gallery-item');
    let foundResults = false;

    galleryItems.forEach(item => {
        const imgElement = item.querySelector('img.gallery-image');
        const imgSrc = imgElement.getAttribute('src');
        const normalizedSrc = imgSrc.startsWith('/') ? imgSrc : '/' + imgSrc;
        const meta = imageMetadata[normalizedSrc] || {};

        const username = (meta.username || '').toLowerCase();
        const filename = (meta.filename || '').toLowerCase();

        let tags = [];
        if (meta.hasMetadata && meta.metadata && meta.metadata.tags) {
            try {
                if (typeof meta.metadata.tags === 'string') {
                    tags = JSON.parse(meta.metadata.tags);
                } else if (Array.isArray(meta.metadata.tags)) {
                    tags = meta.metadata.tags;
                }
            } catch (e) {
                console.error('Error parsing tags:', e);
            }
        }
        const tagsString = (Array.isArray(tags) ? tags.join(' ') : '').toLowerCase();

        if (searchTerms.length === 0) {
            item.style.display = '';
            foundResults = true;
            return;
        }

        const matchesAllTerms = searchTerms.every(term => {
            const parsedSearch = parseSearchQuery(term);

            if (parsedSearch.field) {
                return matchesFieldSearch(meta, parsedSearch);
            } else {
                const matchesUsername = filterUsername && username.includes(parsedSearch.term);
                const matchesFilename = filterFilename && filename.includes(parsedSearch.term);
                const matchesTags = filterTags && tagsString.includes(parsedSearch.term);

                let matchesMetadata = false;

                if (meta.hasMetadata && meta.metadata && filterMetadata) {
                    if (filterCoordinates && meta.metadata.coordinates &&
                        meta.metadata.coordinates.toLowerCase().includes(parsedSearch.term)) {
                        matchesMetadata = true;
                    }

                    if (filterDimension && meta.metadata.dimension &&
                        meta.metadata.dimension.toLowerCase().includes(parsedSearch.term)) {
                        matchesMetadata = true;
                    }

                    if (filterBiome && meta.metadata.biome &&
                        meta.metadata.biome.toLowerCase().includes(parsedSearch.term)) {
                        matchesMetadata = true;
                    }

                    if (filterSystemInfo && meta.metadata.system_info &&
                        meta.metadata.system_info.toLowerCase().includes(parsedSearch.term)) {
                        matchesMetadata = true;
                    }

                    if (filterWorldInfo && meta.metadata.world_info &&
                        meta.metadata.world_info.toLowerCase().includes(parsedSearch.term)) {
                        matchesMetadata = true;
                    }

                    if (filterClientSettings && meta.metadata.client_settings &&
                        meta.metadata.client_settings.toLowerCase().includes(parsedSearch.term)) {
                        matchesMetadata = true;
                    }
                }

                return parsedSearch.term === '' || matchesUsername || matchesFilename || matchesTags || matchesMetadata;
            }
        });

        if (matchesAllTerms) {
            item.style.display = '';
            foundResults = true;
        } else {
            item.style.display = 'none';
        }
    });

    document.getElementById('noResults').style.display = foundResults ? 'none' : 'block';
}

function parseSearchQuery(query) {
    const result = {
        field: null,
        operator: '',
        term: query.trim()
    };

    const fieldMappings = {
        'x:': 'x',
        'y:': 'y',
        'z:': 'z',

        'seed:': 'world_seed',
        'biome:': 'biome',
        'world:': 'world_name',
        'server:': 'server_address',
        'dimension:': 'dimension',
        'dim:': 'dimension',
        'username:': 'username',
        'coordinates:': 'coordinates',
        'location:': 'coordinates',
        'facing:': 'facing_direction',
        'player:': 'player_state',

        'date:': 'date',
        'day:': 'date',
        'time:': 'date',

        'health:': 'health',
        'food:': 'food',
        'air:': 'air',
        'speed:': 'speed',

        'worldtime:': 'time',
        'weather:': 'weather',
        'difficulty:': 'difficulty',

        'file:': 'filename',
        'tags:': 'tags'
    };

    for (const [prefix, fieldName] of Object.entries(fieldMappings)) {
        if (query.toLowerCase().startsWith(prefix)) {
            const valueWithOperator = query.substring(prefix.length).trim();

            const operators = ['>=', '<=', '>', '<', '='];
            let operator = '';
            let value = valueWithOperator;

            for (const op of operators) {
                if (valueWithOperator.startsWith(op)) {
                    operator = op;
                    value = valueWithOperator.substring(op.length).trim();
                    break;
                }
            }

            return {
                field: fieldName,
                operator: operator,
                term: value
            };
        }
    }

    return result;
}

function matchesFieldSearch(meta, search) {
    if (!meta.hasMetadata || !meta.metadata || !search.field) {
        return false;
    }

    if (search.field === 'tags') {
        if (!meta.metadata.tags) return false;

        let tags = [];
        try {
            if (typeof meta.metadata.tags === 'string') {
                tags = JSON.parse(meta.metadata.tags);
            } else if (Array.isArray(meta.metadata.tags)) {
                tags = meta.metadata.tags;
            }
        } catch (e) {
            console.error('Error parsing tags:', e);
            return false;
        }

        const tagsString = (Array.isArray(tags) ? tags.join(' ') : '').toLowerCase();
        return tagsString.includes(search.term);
    }

    if (search.field === 'username') {
        const username = (meta.username || '').toLowerCase();
        if (search.operator === '') {
            return username.includes(search.term);
        }
        return compareValues(username, search.operator, search.term);
    }

    if (search.field === 'filename') {
        const filename = (meta.filename || '').toLowerCase();
        if (search.operator === '') {
            return filename.includes(search.term);
        }
        return compareValues(filename, search.operator, search.term);
    }

    if (search.field === 'x' || search.field === 'y' || search.field === 'z') {
        if (!meta.metadata.coordinates) return false;

        const coordinates = meta.metadata.coordinates.toLowerCase();
        const coordPattern = new RegExp(`${search.field}: (-?\\d+)`, 'i');
        const match = coordinates.match(coordPattern);

        if (match && match[1]) {
            const coordValue = parseInt(match[1], 10);
            const searchValue = parseInt(search.term, 10);

            if (isNaN(searchValue)) return false;

            switch (search.operator) {
                case '>': return coordValue > searchValue;
                case '<': return coordValue < searchValue;
                case '=': return coordValue === searchValue;
                case '>=': return coordValue >= searchValue;
                case '<=': return coordValue <= searchValue;
                default: return String(coordValue).includes(search.term);
            }
        }
        return false;
    }

    if (search.field === 'date') {
        const timestamp = meta.metadata.current_time || meta.timestamp || 0;
        return compareDateValues(timestamp, search.operator, search.term);
    }

    const nestedFieldMappings = {
        'health': 'player_state',
        'food': 'player_state',
        'air': 'player_state',
        'speed': 'player_state',
        'time': 'world_info',
        'weather': 'world_info',
        'difficulty': 'world_info'
    };

    if (nestedFieldMappings[search.field]) {
        const parentField = nestedFieldMappings[search.field];
        if (!meta.metadata[parentField]) return false;

        const parentValue = meta.metadata[parentField].toLowerCase();
        const nestedPattern = new RegExp(`${search.field}: ([^,]+)`, 'i');
        const match = parentValue.match(nestedPattern);

        if (match && match[1]) {
            const fieldValue = match[1].trim();

            if (search.operator === '') {
                return fieldValue.includes(search.term);
            }

            const numericMatch = fieldValue.match(/-?\d+(\.\d+)?/);
            if (numericMatch && !isNaN(parseFloat(search.term))) {
                const fieldNum = parseFloat(numericMatch[0]);
                const searchNum = parseFloat(search.term);

                switch (search.operator) {
                    case '>': return fieldNum > searchNum;
                    case '<': return fieldNum < searchNum;
                    case '=': return fieldNum === searchNum;
                    case '>=': return fieldNum >= searchNum;
                    case '<=': return fieldNum <= searchNum;
                }
            }

            return fieldValue.includes(search.term);
        }
        return false;
    }

    if (!meta.metadata[search.field]) {
        return false;
    }

    const fieldValue = String(meta.metadata[search.field]).toLowerCase();

    if (search.operator === '') {
        return fieldValue.includes(search.term);
    }

    return compareValues(fieldValue, search.operator, search.term);
}

function compareDateValues(timestamp, operator, searchTerm) {
    const itemDate = new Date(Number(timestamp));

    if (searchTerm === 'today') {
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        const tomorrow = new Date(today);
        tomorrow.setDate(tomorrow.getDate() + 1);

        if (!operator || operator === '=') {
            return itemDate >= today && itemDate < tomorrow;
        }
    } else if (searchTerm === 'yesterday') {
        const yesterday = new Date();
        yesterday.setDate(yesterday.getDate() - 1);
        yesterday.setHours(0, 0, 0, 0);

        const today = new Date();
        today.setHours(0, 0, 0, 0);

        if (!operator || operator === '=') {
            return itemDate >= yesterday && itemDate < today;
        }
    } else if (searchTerm.includes('week')) {
        const weekAgo = new Date();
        weekAgo.setDate(weekAgo.getDate() - 7);

        if (!operator) {
            return itemDate >= weekAgo;
        } else {
            switch(operator) {
                case '>': return itemDate > weekAgo;
                case '<': return itemDate < weekAgo;
                case '>=': return itemDate >= weekAgo;
                case '<=': return itemDate <= weekAgo;
                case '=': return false;
            }
        }
    } else if (searchTerm.includes('month')) {
        const monthAgo = new Date();
        monthAgo.setMonth(monthAgo.getMonth() - 1);

        if (!operator) {
            return itemDate >= monthAgo;
        } else {
            switch(operator) {
                case '>': return itemDate > monthAgo;
                case '<': return itemDate < monthAgo;
                case '>=': return itemDate >= monthAgo;
                case '<=': return itemDate <= monthAgo;
                case '=': return false;
            }
        }
    }

    let searchDate;
    if (searchTerm.match(/\d{1,2}\.\d{1,2}\.\d{4}/)) {
        // Format: DD.MM.YYYY
        const [day, month, year] = searchTerm.split('.').map(Number);
        searchDate = new Date(year, month-1, day);
    } else if (searchTerm.match(/\d{1,2}\.\d{4}/)) {
        // Format: MM.YYYY
        const [month, year] = searchTerm.split('.').map(Number);
        searchDate = new Date(year, month-1, 1);
    } else if (searchTerm.match(/\d{4}/)) {
        // Format: YYYY
        const year = parseInt(searchTerm);
        searchDate = new Date(year, 0, 1);
    }

    if (searchDate && !isNaN(searchDate.getTime())) {
        switch(operator) {
            case '>': return itemDate > searchDate;
            case '<': return itemDate < searchDate;
            case '>=': return itemDate >= searchDate;
            case '<=': return itemDate <= searchDate;
            case '=': return itemDate.getFullYear() === searchDate.getFullYear() &&
                        itemDate.getMonth() === searchDate.getMonth() &&
                        itemDate.getDate() === searchDate.getDate();
            default:
                if (searchTerm.match(/\d{1,2}\.\d{1,2}\.\d{4}/)) {
                    return itemDate.getFullYear() === searchDate.getFullYear() &&
                           itemDate.getMonth() === searchDate.getMonth() &&
                           itemDate.getDate() === searchDate.getDate();
                } else if (searchTerm.match(/\d{1,2}\.\d{4}/)) {
                    return itemDate.getFullYear() === searchDate.getFullYear() &&
                           itemDate.getMonth() === searchDate.getMonth();
                } else if (searchTerm.match(/\d{4}/)) {
                    return itemDate.getFullYear() === searchDate.getFullYear();
                }
                return false;
        }
    }

    return String(timestamp).includes(searchTerm);
}

function compareValues(fieldValue, operator, searchValue) {
    try {
        const fieldNum = parseFloat(fieldValue);
        const searchNum = parseFloat(searchValue);

        if (!isNaN(fieldNum) && !isNaN(searchNum)) {
            switch(operator) {
                case '>': return fieldNum > searchNum;
                case '<': return fieldNum < searchNum;
                case '=': return fieldNum === searchNum;
                case '>=': return fieldNum >= searchNum;
                case '<=': return fieldNum <= searchNum;
                default: return fieldValue.includes(searchValue);
            }
        }
    } catch (e) {
    }

    if (operator === '=') {
        return fieldValue === searchValue;
    }

    return fieldValue.includes(searchValue);
}

document.addEventListener('DOMContentLoaded', function() {
    const searchInput = document.getElementById('searchInput');
    if (searchInput) {
        searchInput.addEventListener('keypress', function(event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                searchGallery();
            }
        });
    }
});