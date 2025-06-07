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

document.addEventListener('DOMContentLoaded', function() {
    loadLikedImages();

    const thumbnailsContainer = document.getElementById('thumbnails');
    const modelContainer = document.getElementById('model-left');

    if (thumbnailsContainer) {
        thumbnailsContainer.addEventListener('wheel', function(event) {
            event.preventDefault();
            thumbnailsContainer.scrollLeft += event.deltaY;
        }, { passive: false });
    }

    if (modelContainer) {
        modelContainer.addEventListener('wheel', function(event) {
            event.preventDefault();
        }, { passive: false });
    }
});