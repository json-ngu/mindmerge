<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="Review decision | MindMerge"/>
<%@ include file="/WEB-INF/jspf/head.jspf" %>
<body>
<div class="app">
  <%@ include file="/WEB-INF/jspf/sidebar.jspf" %>

  <main class="app-main manage">
    <p class="back-link"><a href="${pageContext.request.contextPath}/approvals">Back to approvals</a></p>

    <c:choose>
      <c:when test="${empty decision}">
        <p class="alert" role="alert"><c:out value="${error}"/></p>
      </c:when>
      <c:otherwise>
        <header class="manage-head">
          <div>
            <p class="feed-project"><c:out value="${decision.projectName}"/></p>
            <h1 class="review-heading"><c:out value="${decision.title}"/></h1>
            <p class="dash-sub">Proposed by <c:out value="${decision.decidedBy}"/> on <c:out value="${decision.dateAtTime(userZone)}"/></p>
          </div>
        </header>

        <section class="box" aria-labelledby="contextHeading">
          <h2 id="contextHeading">Context</h2>
          <p class="prose"><c:out value="${decision.context}"/></p>
        </section>

        <section class="box" aria-labelledby="optionsHeading">
          <h2 id="optionsHeading">Options considered</h2>
          <ul class="choice-list">
            <c:forEach var="o" items="${options}">
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

        <form class="approve-form" method="post" action="${pageContext.request.contextPath}/approvals">
          <input type="hidden" name="decision" value="${decision.decisionId}">
          <p class="panel-note">Approving makes the selected option the final decision for this project.</p>
          <button class="pill pill-glow" type="submit" name="action" value="approve">Approve decision</button>
        </form>
        <form class="approve-form" method="post" action="${pageContext.request.contextPath}/approvals"
              data-confirm="Reject this proposal? It will be closed without being approved.">
          <input type="hidden" name="decision" value="${decision.decisionId}">
          <p class="panel-note">Rejecting closes this proposal without approving its selected option.</p>
          <button class="pill pill-ghost" type="submit" name="action" value="reject">Reject proposal</button>
        </form>
        <form class="form comment-form" method="post" action="${pageContext.request.contextPath}/approvals">
          <input type="hidden" name="decision" value="${decision.decisionId}">
          <c:if test="${not empty error}"><p class="alert" role="alert"><c:out value="${error}"/></p></c:if>
          <label for="explanation">Explanation for requested changes</label>
          <textarea id="explanation" name="explanation" rows="4" maxlength="${explanationLimit}" required><c:out value="${explanation}"/></textarea>
          <p class="panel-note">Explain what should change. The explanation is added to the decision&#39;s comments.</p>
          <button class="pill pill-glow" type="submit" name="action" value="request_changes">Request changes</button>
        </form>
      </c:otherwise>
    </c:choose>
  </main>
</div>
<script src="${pageContext.request.contextPath}/js/manage.js"></script>
</body>
</html>
