function showModal(src) {
            const modal = document.getElementById('modal');
            const modalImg = document.getElementById('modalImage');
            const loading = document.getElementById('loading');

            modal.style.display = 'flex';
            currentSrc = src;
            currentIndex = images.indexOf(src) + 1;
            updateImageCount(images.length);
            loadImage(src);
            createThumbnails();

            loading.style.display = 'block';
            modalImg.onload = () => {
                loading.style.display = 'none';
                modalImg.classList.add('visible');
            };

            stopSlideshow();
            document.addEventListener('keydown', keyNavigation);
            fetchComments(src);
        }

        function loadImage(src) {
            const modalImg = document.getElementById('modalImage');
            modalImg.src = src;
            modalImg.classList.remove('visible');
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
            }
        }

        function previousImage() {
            currentIndex = (currentIndex - 1 + images.length) % images.length;
            loadImage(images[currentIndex]);
            updateImageCount(images.length);
        }

        function nextImage() {
            currentIndex = (currentIndex + 1) % images.length;
            loadImage(images[currentIndex]);
            updateImageCount(images.length);
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

        function createThumbnails() {
            const thumbnailsContainer = document.getElementById('thumbnails');
            thumbnailsContainer.innerHTML = '';
            images.forEach((image, index) => {
                const thumbnail = document.createElement('img');
                thumbnail.src = image;
                thumbnail.onclick = () => loadImage(image);
                if (image === currentSrc) {
                    thumbnail.classList.add('selected');
                }
                thumbnailsContainer.appendChild(thumbnail);
            });
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
                    commentsContainer.innerHTML = '';
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

document.addEventListener('DOMContentLoaded', function() {
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