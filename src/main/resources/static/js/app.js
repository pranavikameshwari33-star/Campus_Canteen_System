// Small helpers used on all pages. The website works without them; they only improve usability.

// 1. Ask for confirmation on forms marked with data-confirm="...".
// 2. Disable the clicked button after submitting, so an order cannot be sent twice,
//    and show "Please wait..." while the page loads.
document.addEventListener('submit', function (event) {
    var form = event.target;
    if (form.dataset.confirm && !window.confirm(form.dataset.confirm)) {
        event.preventDefault();
        return;
    }
    var button = event.submitter;
    if (button) {
        setTimeout(function () {
            button.disabled = true;
            if (button.dataset.loading) {
                button.textContent = button.dataset.loading;
            }
        }, 0);
    }
});

// 3. Reload pages marked with data-refresh="seconds" (order status and queue pages),
//    so customers and staff see status changes without pressing refresh.
document.addEventListener('DOMContentLoaded', function () {
    var element = document.querySelector('[data-refresh]');
    if (element) {
        var seconds = parseInt(element.dataset.refresh, 10) || 30;
        setTimeout(function () { window.location.reload(); }, seconds * 1000);
    }
});

// 4. Check the size of a chosen image before it is uploaded (inputs marked with data-max-bytes),
//    so a file that is too large is refused at once instead of after a long upload.
//    The server checks the size again; this is only for convenience.
document.addEventListener('change', function (event) {
    var input = event.target;
    if (input.type !== 'file' || !input.dataset.maxBytes || !input.files.length) {
        return;
    }
    var tooLarge = input.files[0].size > parseInt(input.dataset.maxBytes, 10);
    input.setCustomValidity(tooLarge ? 'The image is too large. The maximum size is 5 MB.' : '');
    input.reportValidity();
});

// 5. Food item form: show a preview of the photo chosen in the list or picked for upload.
document.addEventListener('change', function (event) {
    var input = event.target;
    var preview = document.getElementById('photo-preview');
    var empty = document.getElementById('photo-preview-empty');
    if (!preview || !empty) {
        return;
    }
    var url = null;
    if (input.hasAttribute('data-photo-select')) {
        url = input.value || input.dataset.suggested || null;
    } else if (input.hasAttribute('data-photo-upload') && input.files.length) {
        url = URL.createObjectURL(input.files[0]);
    } else {
        return;
    }
    preview.hidden = !url;
    empty.hidden = !!url;
    if (url) {
        preview.src = url;
    }
});

// 6. If a food photo cannot be loaded (for example a broken link), show the "No photo" box instead.
document.addEventListener('error', function (event) {
    var img = event.target;
    if (img.tagName !== 'IMG' || !img.closest('.food-photo, .item-cell, .photo-preview')) {
        return;
    }
    var box = document.createElement('div');
    box.className = 'img-placeholder' + (img.classList.contains('thumb') ? ' thumb' : '');
    box.textContent = 'No photo';
    if (img.id === 'photo-preview') {
        img.hidden = true;
        document.getElementById('photo-preview-empty').hidden = false;
    } else {
        img.replaceWith(box);
    }
}, true);
// Photos that already failed before this script ran (it is loaded with "defer").
document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.food-photo img, .item-cell img').forEach(function (img) {
        if (img.complete && img.naturalWidth === 0 && img.getAttribute('src')) {
            img.dispatchEvent(new Event('error'));
        }
    });
});
