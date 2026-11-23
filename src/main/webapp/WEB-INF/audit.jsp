<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<c:set var="pageTitle" value="Audit log | MindMerge"/>
<c:set var="pickerAction" value="/audit"/>
<%@ include file="/WEB-INF/jspf/head.jspf" %>
<body>
<div class="app">
  <%@ include file="/WEB-INF/jspf/sidebar.jspf" %>

  <main class="app-main manage">
    <header class="manage-head">
      <div>
        <h1>Audit log</h1>
        <p class="dash-sub"><c:out value="${company.name}"/></p>
      </div>
      <%@ include file="/WEB-INF/jspf/company-picker.jspf" %>
    </header>

    <c:if test="${not empty error}">
      <p class="alert" role="alert"><c:out value="${error}"/></p>
    </c:if>

    <c:if test="${empty error}">
      <section class="box" aria-labelledby="filterHeading">
        <h2 id="filterHeading">Filter</h2>
        <form class="audit-filters" method="get" action="${pageContext.request.contextPath}/audit">
          <input type="hidden" name="company" value="${company.companyId}">
          <div class="audit-field">
            <label for="person">Person</label>
            <select id="person" class="select" name="person">
              <option value="">Everyone</option>
              <c:forEach var="p" items="${people}">
                <option value="${p.userId}" ${p.userId == person ? 'selected' : ''}><c:out value="${p.fullName}"/></option>
              </c:forEach>
            </select>
          </div>
          <div class="audit-field">
            <label for="type">Type</label>
            <select id="type" class="select" name="type">
              <option value="">All types</option>
              <c:forEach var="t" items="${types}">
                <option value="${t.key}" ${t.key eq type ? 'selected' : ''}><c:out value="${t.value}"/></option>
              </c:forEach>
            </select>
          </div>
          <div class="audit-field">
            <label for="from">From</label>
            <input id="from" class="input" name="from" type="date" value="${from}">
          </div>
          <div class="audit-field">
            <label for="to">To</label>
            <input id="to" class="input" name="to" type="date" value="${to}">
          </div>
          <div class="audit-actions">
            <button class="pill pill-glow pill-sm" type="submit">Apply filters</button>
            <c:if test="${filtered}">
              <a class="pill pill-ghost pill-sm" href="${pageContext.request.contextPath}/audit?company=${company.companyId}">Clear</a>
            </c:if>
          </div>
        </form>
      </section>

      <section class="box" aria-labelledby="logHeading">
        <h2 id="logHeading">Changes</h2>
        <c:choose>
          <c:when test="${empty entries}">
            <p class="dash-empty"><c:out value="${filtered ? 'No changes match these filters.' : 'No changes have been recorded for this company.'}"/></p>
          </c:when>
          <c:otherwise>
            <ol class="row-list audit-list">
              <c:forEach var="e" items="${entries}">
                <li class="row audit-row">
                  <div class="audit-text">
                    <p class="audit-action">
                      <c:out value="${e.before}"/>
                      <c:if test="${e.named}">
                        <c:choose>
                          <c:when test="${e.decisionId > 0}">
                            <a href="${pageContext.request.contextPath}/decisions/details?id=${e.decisionId}"><c:out value="${empty e.entityName ? 'a removed item' : e.entityName}"/></a>
                          </c:when>
                          <c:when test="${e.projectId > 0}">
                            <a href="${pageContext.request.contextPath}/projects?project=${e.projectId}"><c:out value="${empty e.entityName ? 'a removed item' : e.entityName}"/></a>
                          </c:when>
                          <c:otherwise>
                            <strong><c:out value="${empty e.entityName ? 'a removed item' : e.entityName}"/></strong>
                          </c:otherwise>
                        </c:choose>
                      </c:if>
                      <c:out value="${e.after}"/>
                    </p>
                    <c:if test="${not empty e.detail}">
                      <p class="audit-detail"><c:out value="${e.detail}"/></p>
                    </c:if>
                  </div>
                  <p class="audit-meta">
                    <span><c:out value="${e.actor}"/></span>
                    <span><c:out value="${e.dateTime(userZone)}"/></span>
                  </p>
                </li>
              </c:forEach>
            </ol>

            <c:url var="previousPageUrl" value="/audit">
              <c:param name="company" value="${company.companyId}"/>
              <c:if test="${person > 0}"><c:param name="person" value="${person}"/></c:if>
              <c:if test="${not empty type}"><c:param name="type" value="${type}"/></c:if>
              <c:if test="${not empty from}"><c:param name="from" value="${from}"/></c:if>
              <c:if test="${not empty to}"><c:param name="to" value="${to}"/></c:if>
              <c:param name="page" value="${page - 1}"/>
            </c:url>
            <c:url var="nextPageUrl" value="/audit">
              <c:param name="company" value="${company.companyId}"/>
              <c:if test="${person > 0}"><c:param name="person" value="${person}"/></c:if>
              <c:if test="${not empty type}"><c:param name="type" value="${type}"/></c:if>
              <c:if test="${not empty from}"><c:param name="from" value="${from}"/></c:if>
              <c:if test="${not empty to}"><c:param name="to" value="${to}"/></c:if>
              <c:param name="page" value="${page + 1}"/>
            </c:url>
            <nav class="pagination" aria-label="Audit log pages">
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
          </c:otherwise>
        </c:choose>
      </section>
    </c:if>
  </main>
</div>
<script src="${pageContext.request.contextPath}/js/manage.js"></script>
</body>
</html>
