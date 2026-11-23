<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<c:set var="pageTitle" value="Tags | MindMerge"/>
<c:set var="pickerAction" value="/tags"/>
<%@ include file="/WEB-INF/jspf/head.jspf" %>
<body>
<div class="app">
  <%@ include file="/WEB-INF/jspf/sidebar.jspf" %>

  <main class="app-main manage">
    <header class="manage-head">
      <div>
        <h1>Tags</h1>
        <p class="dash-sub"><c:out value="${company.name}"/></p>
      </div>
      <%@ include file="/WEB-INF/jspf/company-picker.jspf" %>
    </header>

    <%@ include file="/WEB-INF/jspf/notice.jspf" %>
    <c:if test="${not empty error}">
      <p class="alert" role="alert"><c:out value="${error}"/></p>
    </c:if>

    <c:if test="${empty error}">
      <section class="box" aria-labelledby="newTagHeading">
        <h2 id="newTagHeading">Create a tag</h2>
        <p class="panel-note">Members of <c:out value="${company.name}"/> choose from these tags when they record a decision.</p>
        <form class="inline-form" method="post" action="${pageContext.request.contextPath}/tags">
          <input type="hidden" name="company" value="${company.companyId}">
          <input type="hidden" name="action" value="create">
          <label class="visually-hidden" for="tagName">Tag name</label>
          <input id="tagName" class="input" name="name" type="text" required maxlength="${tagLimit}" placeholder="For example: performance">
          <button class="pill pill-glow" type="submit">Create tag</button>
        </form>
      </section>

      <section class="box" aria-labelledby="tagsHeading">
        <h2 id="tagsHeading">Tags <span class="count"><c:out value="${fn:length(tags)}"/></span></h2>
        <c:choose>
          <c:when test="${empty tags}">
            <p class="dash-empty">No tags have been created for <c:out value="${company.name}"/>.</p>
          </c:when>
          <c:otherwise>
            <ul class="row-list">
              <c:forEach var="t" items="${tags}">
                <li class="row tag-row">
                  <p class="row-title">
                    <span class="tag tag-${t.color}"><c:out value="${t.name}"/></span>
                    <span class="muted"><c:out value="${t.uses}"/> <c:out value="${t.uses == 1 ? 'decision' : 'decisions'}"/></span>
                  </p>
                  <form class="inline-form tag-rename" method="post" action="${pageContext.request.contextPath}/tags">
                    <input type="hidden" name="company" value="${company.companyId}">
                    <input type="hidden" name="action" value="rename">
                    <input type="hidden" name="tag" value="${t.tagId}">
                    <label class="visually-hidden" for="rename${t.tagId}">New name for <c:out value="${t.name}"/></label>
                    <input id="rename${t.tagId}" class="input" name="name" type="text" required maxlength="${tagLimit}" value="<c:out value='${t.name}'/>">
                    <button class="pill pill-ghost pill-sm" type="submit">Rename</button>
                  </form>
                </li>
              </c:forEach>
            </ul>
          </c:otherwise>
        </c:choose>
      </section>
    </c:if>
  </main>
</div>
<script src="${pageContext.request.contextPath}/js/manage.js"></script>
</body>
</html>
