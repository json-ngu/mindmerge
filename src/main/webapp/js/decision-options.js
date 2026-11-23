// Create decision page: add and remove option cards. A decision keeps at least two options,
// so Remove only shows when there are more. Each card's radio selects the recommended option;
// its value is the card's position. Without JS the form posts the options it shows.
(function () {
  "use strict";

  var MIN_OPTIONS = 2;
  var MAX_OPTIONS = 10; // DecisionServlet.MAX_OPTIONS
  var list = document.getElementById("optionList");
  var addButton = document.getElementById("addOption");
  if (!list || !addButton) return;

  function cards() {
    return list.querySelectorAll(".option-box");
  }

  // Number the cards 1..n and keep each label pointing at its own field.
  function renumber() {
    var all = cards();
    Array.prototype.forEach.call(all, function (card, index) {
      var n = index + 1;
      card.querySelector("h3").textContent = "Option " + n;
      var labels = card.querySelectorAll("label");
      var name = card.querySelector("input[type=text]");
      var description = card.querySelector("textarea");
      name.id = "optionLabel" + n;
      description.id = "optionDescription" + n;
      labels[0].htmlFor = name.id;
      labels[1].htmlFor = description.id;
      card.querySelector("input[type=radio]").value = n;
      card.querySelector(".remove-option").hidden = all.length <= MIN_OPTIONS;
    });
    addButton.hidden = all.length >= MAX_OPTIONS;
  }

  // A new card is a copy of the first one with its fields cleared.
  addButton.addEventListener("click", function () {
    var card = cards()[0].cloneNode(true);
    card.querySelector("input[type=text]").value = "";
    card.querySelector("textarea").value = "";
    card.querySelector("input[type=radio]").checked = false;
    list.appendChild(card);
    renumber();
    card.querySelector("input[type=text]").focus();
  });

  list.addEventListener("click", function (event) {
    var button = event.target.closest(".remove-option");
    if (!button || cards().length <= MIN_OPTIONS) return;
    button.closest(".option-box").remove();
    renumber();
    addButton.focus();
  });

  addButton.hidden = false;
  renumber();
})();

// Tag picker: each company has its own tags, so show only the selected project's company's tags.
// Without JavaScript every tag shows, and the server saves only tags of the project's company.
(function () {
  "use strict";

  var select = document.getElementById("projectId");
  var picker = document.getElementById("tagPicker");
  if (!select || !picker) return;

  var choices = picker.querySelectorAll(".tag-choice");
  var note = picker.querySelector(".tag-picker-note");

  function update() {
    var option = select.options[select.selectedIndex];
    var company = option ? option.getAttribute("data-company") : null;
    var shown = 0;
    for (var i = 0; i < choices.length; i++) {
      var match = company !== null && choices[i].getAttribute("data-company") === company;
      choices[i].hidden = !match;
      if (match) {
        shown++;
      } else {
        choices[i].querySelector("input").checked = false;
      }
    }
    note.hidden = shown > 0;
    note.textContent = company === null
      ? "Select a project to choose its tags."
      : "This project's company has no tags yet.";
  }

  select.addEventListener("change", update);
  update();
})();
