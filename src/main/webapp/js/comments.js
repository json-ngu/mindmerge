// Decision discussion: count against the textarea's maxlength, keeping the limit in one place.
(function () {
  "use strict";

  var body = document.getElementById("commentBody");
  var counter = document.getElementById("commentRemaining");
  if (!body || !counter) return;

  function updateRemaining() {
    // JS length uses the same UTF-16 units as HTML maxlength and Java String.length().
    var remaining = body.maxLength - body.value.length;
    var count = Math.abs(remaining);
    counter.textContent = count + (count === 1 ? " character " : " characters ") +
      (remaining < 0 ? "over the limit." : "remaining.");
  }

  // Input includes typing, pasting, cutting and undo. Initialize preserved validation-error text too.
  body.addEventListener("input", updateRemaining);
  window.addEventListener("pageshow", updateRemaining); // Browser Back may restore a draft.
  updateRemaining();
})();
