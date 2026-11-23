<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<c:set var="pageTitle" value="${empty project ? 'Project' : project.name} | MindMerge"/>
<%@ include file="/WEB-INF/jspf/head.jspf" %>
<body>
<div class="app">
  <%@ include file="/WEB-INF/jspf/sidebar.jspf" %>

  <main class="app-main manage">
    <p class="back-link"><a href="${pageContext.request.contextPath}/projects">Back to projects</a></p>

    <c:choose>
      <c:when test="${empty project}">
        <p class="alert" role="alert"><c:out value="${error}"/></p>
      </c:when>
      <c:otherwise>
        <header class="manage-head">
          <div>
            <p class="feed-project"><c:out value="${project.teamName}"/>, <c:out value="${project.companyName}"/></p>
            <h1 class="review-heading"><c:out value="${project.name}"/></h1>
            <p class="project-meta">
              <span class="project-status project-${project.status}"><c:out value="${project.statusLabel}"/></span>
              <span><c:out value="${project.decisionCount}"/> <c:out value="${project.decisionCount == 1 ? 'decision' : 'decisions'}"/></span>
              <c:if test="${not empty project.lastActivityDate}"><span>Last activity on <c:out value="${project.lastActivityDate}"/></span></c:if>
            </p>
          </div>
          <c:if test="${project.member}">
            <a class="pill pill-glow pill-sm" href="${pageContext.request.contextPath}/decisions/create?project=${project.projectId}">Create decision</a>
          </c:if>
        </header>

        <%@ include file="/WEB-INF/jspf/notice.jspf" %>

        <section class="box" aria-labelledby="descriptionHeading">
          <h2 id="descriptionHeading">Description</h2>
          <c:choose>
            <c:when test="${not empty project.description}">
              <p class="prose"><c:out value="${project.description}"/></p>
            </c:when>
            <c:otherwise>
              <p class="prose muted">No description has been provided.</p>
            </c:otherwise>
          </c:choose>
        </section>

        <%-- Team lead or company owner/admin; ProjectServlet re-checks on submit. --%>
        <c:if test="${project.canManage}">
          <section class="box" aria-labelledby="manageHeading">
            <h2 id="manageHeading">Manage project</h2>
            <form class="form project-form" method="post" action="${pageContext.request.contextPath}/projects">
              <input type="hidden" name="action" value="update">
              <input type="hidden" name="project" value="${project.projectId}">

              <label for="projectName">Name</label>
              <input id="projectName" name="name" type="text" required maxlength="180" value="<c:out value='${project.name}'/>">

              <label for="status">Status</label>
              <select id="status" class="select" name="status">
                <c:forEach var="s" items="${statuses}">
                  <option value="${s.key}" ${s.key eq project.status ? 'selected' : ''}><c:out value="${s.value}"/></option>
                </c:forEach>
              </select>

              <label for="description">Description <span class="optional">(optional)</span></label>
              <textarea id="description" name="description" maxlength="10000"><c:out value="${project.description}"/></textarea>

              <button class="pill pill-glow" type="submit">Save changes</button>
            </form>
          </section>
        </c:if>

        <section class="box project-decisions" aria-labelledby="decisionsHeading">
          <h2 id="decisionsHeading">Decisions <span class="count"><c:out value="${fn:length(decisions)}"/></span></h2>
          <c:choose>
            <c:when test="${empty decisions}">
              <p class="dash-empty">No decisions have been recorded for this project.</p>
            </c:when>
            <c:otherwise>
              <ol class="feed">
                <c:forEach var="d" items="${decisions}">
                  <%@ include file="/WEB-INF/jspf/decision-item.jspf" %>
                </c:forEach>
              </ol>
            </c:otherwise>
          </c:choose>
        </section>
      </c:otherwise>
    </c:choose>
  </main>
</div>
</body>
</html>
