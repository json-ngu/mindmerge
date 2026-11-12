<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<c:set var="pageTitle" value="${empty decision ? 'Decision' : decision.title} | MindMerge"/>
<%@ include file="/WEB-INF/jspf/head.jspf" %>
<body>
<div class="app">
  <%@ include file="/WEB-INF/jspf/sidebar.jspf" %>

  <main class="app-main manage">
    <c:choose>
      <c:when test="${empty decision}">
        <p class="back-link"><a href="${pageContext.request.contextPath}/projects">Back to projects</a></p>
        <p class="alert" role="alert"><c:out value="${error}"/></p>
      </c:when>
      <c:otherwise>
        <p class="back-link"><a href="${pageContext.request.contextPath}/projects?project=${decision.projectId}">Back to project</a></p>

        <header class="manage-head">
          <div>
            <p class="feed-project"><c:out value="${decision.projectName}"/></p>
            <h1 class="review-heading"><c:out value="${decision.title}"/></h1>
            <p class="project-meta">
              <span class="status status-${decision.status}"><c:out value="${decision.status}"/></span>
              <span>Proposed by <c:out value="${decision.decidedBy}"/></span>
              <span><c:out value="${decision.status eq 'proposed' ? 'Proposed on' : 'Decided on'}"/> <c:out value="${decision.dateAtTime(userZone)}"/></span>
            </p>
            <c:if test="${not empty decision.replacedBy}">
              <p class="feed-replaced">Replaced by <span><c:out value="${decision.replacedBy}"/></span></p>
            </c:if>
            <c:if test="${not empty decision.tags}">
              <ul class="tags">
                <c:forEach var="t" items="${decision.tags}">
                  <li class="tag tag-${t.color}"><c:out value="${t.name}"/></li>
                </c:forEach>
              </ul>
            </c:if>
          </div>
        </header>

        <section class="box" aria-labelledby="contextHeading">
          <h2 id="contextHeading">Context</h2>
          <p class="prose"><c:out value="${decision.context}"/></p>
        </section>

        <section class="box" aria-labelledby="optionsHeading">
          <h2 id="optionsHeading">Options considered</h2>
          <ul class="choice-list">
            <c:forEach var="o" items="${decisionOptions}">
              <li class="choice ${o.chosen ? 'is-selected' : ''}">
                <span class="choice-body">
                  <span class="choice-label"><c:out value="${o.label}"/></span>
                  <c:if test="${not empty o.description}">
                    <span class="choice-description"><c:out value="${o.description}"/></span>
                  </c:if>
                </span>
                <c:if test="${o.chosen}"><span class="selected-badge">Selected</span></c:if>
              </li>
            </c:forEach>
          </ul>
        </section>

        <section class="box" aria-labelledby="rationaleHeading">
          <h2 id="rationaleHeading">Rationale</h2>
          <c:choose>
            <c:when test="${not empty decision.rationale}">
              <p class="prose"><c:out value="${decision.rationale}"/></p>
            </c:when>
            <c:otherwise>
              <p class="prose muted">No rationale was provided.</p>
            </c:otherwise>
          </c:choose>
        </section>

        <%@ include file="/WEB-INF/jspf/comments.jspf" %>

        <section class="box project-decisions" aria-labelledby="decisionsHeading">
          <h2 id="decisionsHeading">Decisions in this project <span class="count"><c:out value="${fn:length(decisions)}"/></span></h2>
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
