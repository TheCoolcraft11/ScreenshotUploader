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

            if (confirm(`Are you sure you want to delete the image "${imageName}"?`)) {
                fetch(`delete/${imageName}`, {
                    method: 'DELETE',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${sessionStorage.getItem('authToken')}`
                    },
                })
                    .then((response) => {
                     if(response.ok) {
                     alert("Image deleted successfully!");
                     }else {
                     alert("Failed to delete Image!");
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