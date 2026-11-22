<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<c:set var="pageTitle" value="Decisions | MindMerge"/>
<%@ include file="/WEB-INF/jspf/head.jspf" %>
<body>
<div class="app">
  <%@ include file="/WEB-INF/jspf/sidebar.jspf" %>

  <main class="app-main manage decisions-page">
    <header class="manage-head">
      <div>
        <h1>Decisions</h1>
        <p class="dash-sub">Search and filter your decisions.</p>
      </div>
    </header>

      <c:if test="${viewer.inCompany}">
      <form class="dash-search" action="${pageContext.request.contextPath}/decisions" method="get" role="search">
        <label class="visually-hidden" for="q">Search decisions</label>
        <input id="q" name="q" type="search" value="<c:out value='${q}'/>" placeholder="Search decisions…" autocomplete="off">

        <details class="advanced-filters">
        <summary>Advanced filters</summary>

        <div class="box advanced-filter-panel">

        <div class="filter-row">
            <label for="projectPicker">Project</label>
            <select id="projectPicker">
              <option value="">Choose a project</option>

              <c:forEach var="p" items="${projects}">
                <option value="${p.projectId}">
                  <c:out value="${p.name}"/>
                </option>
              </c:forEach>
            </select>

            <button class="pill pill-ghost pill-sm" type="button" onclick="addFilter('project', 'projectPicker', 'projectFilters')"> + Add project filter </button>
        </div>

        <div id="projectFilters" class="active-filters ${empty projectIds ? 'is-empty' : ''}">
          <c:forEach var="projectId" items="${projectIds}">
            <c:forEach var="p" items="${projects}">
              <c:if test="${projectId eq p.projectId}">
                <span class="filter-chip">
                  <c:out value="${p.name}"/>
                  <input type="hidden" name="project" value="${projectId}">
                  <button type="button" onclick="this.parentElement.remove()">×</button>
                </span>
              </c:if>
            </c:forEach>
          </c:forEach>
        </div>

        <div class="filter-row">
          <label for="tagPicker">Tag</label>
          <select id="tagPicker">
            <option value="">Choose a tag</option>
            <c:forEach var="t" items="${tags}">
              <option value="<c:out value='${t.name}'/>">
                <c:out value="${t.name}"/>
              </option>
            </c:forEach>
          </select>

          <button class="pill pill-ghost pill-sm" type="button" onclick="addFilter('tag', 'tagPicker', 'tagFilters')">+ Add tag filter</button>
        </div>
        <div id="tagFilters" class="active-filters ${empty tagNames ? 'is-empty' : ''}">
          <c:forEach var="tagName" items="${tagNames}">
            <span class="filter-chip">
              <c:out value="${tagName}"/>
              <input type="hidden" name="tag" value="${tagName}">
              <button type="button" onclick="this.parentElement.remove()">×</button>
            </span>
          </c:forEach>
        </div>

        <div class="filter-row">
          <label for="authorPicker">Author</label>
          <input id="authorPicker" type="text" placeholder="Filter by author">
          <button class="pill pill-ghost pill-sm" type="button" onclick="addTextFilter('author', 'authorPicker', 'authorFilters')">+ Add author</button>
        </div>
        <div id="authorFilters" class="active-filters ${empty authorNames ? 'is-empty' : ''}">
          <c:forEach var="authorName" items="${authorNames}">
            <span class="filter-chip">
              <c:out value="${authorName}"/>
              <input type="hidden" name="author" value="${authorName}">
              <button type="button" onclick="this.parentElement.remove()">×</button>
            </span>
          </c:forEach>
        </div>

        <div class="filter-row">
          <label for="statusPicker">Status</label>
          <select id="statusPicker">
            <option value="">Choose a status</option>
            <option value="Proposed">Proposed</option>
            <option value="Decided">Decided</option>
            <option value="Superseded">Superseded</option>
            <option value="Rejected">Rejected</option>
          </select>

          <button class="pill pill-ghost pill-sm" type="button" onclick="addFilter('status', 'statusPicker', 'statusFilters')">+ Add status</button>
        </div>
        <div id="statusFilters" class="active-filters ${empty statuses ? 'is-empty' : ''}">
          <c:forEach var="statusValue" items="${statuses}">
            <span class="filter-chip">
              <c:out value="${statusValue}"/>
              <input type="hidden" name="status" value="${statusValue}">
              <button type="button" onclick="this.parentElement.remove()">×</button>
            </span>
          </c:forEach>
        </div>

        <div class="search-action">
          <button class="pill pill-glow pill-sm" type="submit">Apply filters</button>
          <a href="${pageContext.request.contextPath}/decisions">Clear filters</a>
        </div>

        </div>
        </details>
        
        <c:if test="${view eq 'mine'}"><input type="hidden" name="view" value="mine"></c:if>

      </form>
      </c:if>
      <p class="visually-hidden" id="searchStatus" role="status" aria-live="polite"></p>

    <%@ include file="/WEB-INF/jspf/notice.jspf" %>
    <c:if test="${not empty error}">
      <p class="alert" role="alert"><c:out value="${error}"/></p>
    </c:if>

    <c:if test="${empty error and viewer.inCompany}">

      <div class="decisions-grid">
        <section class="decisions-feed" id="feed" aria-labelledby="feedHeading">
          <div class="section-bar">
            <h2 id="feedHeading">
              <c:choose>
                <c:when test="${not empty q and not empty tag}">Results for “<c:out value="${q}"/>” in “<c:out value="${tag}"/>”</c:when>
                <c:when test="${not empty q}">Results for “<c:out value="${q}"/>”</c:when>
                <c:when test="${not empty tag}">Tagged “<c:out value="${tag}"/>”</c:when>
                <c:otherwise>Decisions</c:otherwise>
              </c:choose>
              <c:if test="${not empty q or not empty tag}"> <span class="muted"><c:out value="${fn:length(feed)}"/> <c:out value="${fn:length(feed) == 1 ? 'decision' : 'decisions'}"/></span></c:if>
            </h2>
            <nav class="segmented" aria-label="Decision filter">
              <%-- Preserve both filters when switching between all decisions and the viewer's own. --%>
              <c:url var="teamUrl" value="/decisions">
                <c:if test="${not empty q}"><c:param name="q" value="${q}"/></c:if>
                <c:forEach var="projectId" items="${projectIds}"><c:param name="project" value="${projectId}"/></c:forEach>
                <c:forEach var="statusValue" items="${statuses}"><c:param name="status" value="${statusValue}"/></c:forEach>
                <c:forEach var="tagName" items="${tagNames}"><c:param name="tag" value="${tagName}"/></c:forEach>
                <c:forEach var="authorName" items="${authorNames}"><c:param name="author" value="${authorName}"/></c:forEach>
              </c:url>
              <c:url var="mineUrl" value="/decisions">
                <c:param name="view" value="mine"/>
                <c:if test="${not empty q}"><c:param name="q" value="${q}"/></c:if>
                <c:forEach var="projectId" items="${projectIds}"><c:param name="project" value="${projectId}"/></c:forEach>
                <c:forEach var="statusValue" items="${statuses}"><c:param name="status" value="${statusValue}"/></c:forEach>
                <c:forEach var="tagName" items="${tagNames}"><c:param name="tag" value="${tagName}"/></c:forEach>
                <c:forEach var="authorName" items="${authorNames}"><c:param name="author" value="${authorName}"/></c:forEach>
              </c:url>
              <a href="${teamUrl}" class="${view eq 'team' ? 'is-active' : ''}" ${view eq 'team' ? 'aria-current="page"' : ''}>All decisions</a>
              <a href="${mineUrl}" class="${view eq 'mine' ? 'is-active' : ''}" ${view eq 'mine' ? 'aria-current="page"' : ''}>My decisions</a>
            </nav>
          </div>

          <c:choose>
            <c:when test="${empty feed and not empty tag}">
              <p class="dash-empty">No decisions match the <c:out value="${tag}"/> tag<c:if test="${not empty q}"> and “<c:out value="${q}"/>”</c:if>.</p>
            </c:when>
            <c:when test="${empty feed and not empty q}">
              <p class="dash-empty">No decisions match “<c:out value="${q}"/>”. Try a different search term.</p>
            </c:when>
            <c:when test="${empty feed and view eq 'mine'}">
              <p class="dash-empty">You have not recorded any decisions.</p>
            </c:when>
            <c:when test="${empty feed}">
              <p class="dash-empty">No decisions have been recorded on your teams.</p>
            </c:when>
            <c:otherwise>
              <ol class="feed">
                <c:forEach var="d" items="${feed}">
                  <%@ include file="/WEB-INF/jspf/decision-item.jspf" %>
                </c:forEach>
              </ol>
            </c:otherwise>
          </c:choose>
          <c:url var="previousPageUrl" value="/decisions">
            <c:param name="page" value="${page - 1}"/>
            <c:if test="${view eq 'mine'}"><c:param name="view" value="mine"/></c:if>
            <c:if test="${not empty q}"><c:param name="q" value="${q}"/></c:if>

            <c:forEach var="projectId" items="${projectIds}"><c:param name="project" value="${projectId}"/></c:forEach>
            <c:forEach var="statusValue" items="${statuses}"><c:param name="status" value="${statusValue}"/></c:forEach>
            <c:forEach var="tagName" items="${tagNames}"><c:param name="tag" value="${tagName}"/></c:forEach>
            <c:forEach var="authorName" items="${authorNames}"><c:param name="author" value="${authorName}"/></c:forEach>
          </c:url>

          <c:url var="nextPageUrl" value="/decisions">
            <c:param name="page" value="${page + 1}"/>
            <c:if test="${view eq 'mine'}"><c:param name="view" value="mine"/></c:if>
            <c:if test="${not empty q}"><c:param name="q" value="${q}"/></c:if>

            <c:forEach var="projectId" items="${projectIds}"><c:param name="project" value="${projectId}"/></c:forEach>
            <c:forEach var="statusValue" items="${statuses}"><c:param name="status" value="${statusValue}"/></c:forEach>
            <c:forEach var="tagName" items="${tagNames}"><c:param name="tag" value="${tagName}"/></c:forEach>
            <c:forEach var="authorName" items="${authorNames}"><c:param name="author" value="${authorName}"/></c:forEach>
          </c:url>

          <nav class="pagination" aria-label="Decision pages">
            <c:choose> 
              <c:when test="${page > 1}"><a class="pagination-link" href="${previousPageUrl}">Previous</a></c:when>
              <c:otherwise><span class="pagination-link is-disabled" aria-disabled="true">Previous</span></c:otherwise>
            </c:choose>
            <span class="pagination-current">Page <c:out value="${page}"/></span>
            <c:choose>
              <c:when test="${hasNext}"><a class="pagination-link" href="${nextPageUrl}">Next</a></c:when>
              <c:otherwise><span class="pagination-link is-disabled" aria-disabled="true">Next</span></c:otherwise>
            </c:choose>
          </nav>

        </section>

      </div>
    </c:if>
  </main>
</div>

<script src="${pageContext.request.contextPath}/js/dashboard.js"></script>
<script>
function addFilter(type, pickerId, containerId) {
    const picker = document.getElementById(pickerId);
    const container = document.getElementById(containerId);

    if (!picker.value) {
        return;
    }

    const value = picker.value;
    const text = picker.options[picker.selectedIndex].text;

    addChip(type, value, text, container);

    picker.value = "";
}

function addTextFilter(type, inputId, containerId) {
    const input = document.getElementById(inputId);
    const container = document.getElementById(containerId);
    const value = input.value.trim();

    if (!value) {
        return;
    }

    addChip(type, value, value, container);
    input.value = "";
}

function addChip(type, value, text, container) {
    const existingInputs = container.querySelectorAll("input");

    for (const input of existingInputs) {
        if (input.value === value) {
            return;
        }
    }

    const chip = document.createElement("span");
    chip.className = "filter-chip";

    const hiddenInput = document.createElement("input");
    hiddenInput.type = "hidden";
    hiddenInput.name = type;
    hiddenInput.value = value;

    const removeButton = document.createElement("button");
    removeButton.type = "button";
    removeButton.textContent = "×";
    removeButton.onclick = function () {
        removeFilter(removeButton);
    };

    chip.append(text + " ");
    chip.appendChild(hiddenInput);
    chip.appendChild(removeButton);
    container.appendChild(chip);

    container.classList.remove("is-empty");
}

function removeFilter(button) {
    const chip = button.parentElement;
    const container = chip.parentElement;

    chip.remove();

    if (!container.querySelector(".filter-chip")) {
        container.classList.add("is-empty");
    }
}
</script>
</body>
</html>
