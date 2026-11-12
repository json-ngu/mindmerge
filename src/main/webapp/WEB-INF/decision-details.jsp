<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<c:set var="pageTitle"
       value="Decision Details | MindMerge"/>

<%@ include file="/WEB-INF/jspf/head.jspf" %>

<body>
<div class="app">

    <%@ include file="/WEB-INF/jspf/sidebar.jspf" %>

    <main class="app-main manage">
        <p class="back-link"><a href="#" onclick="history.back(); return false;">Back to previous page</a></p>
        
        <header class="manage-head">
          <div>
            <p class="feed-project"><c:out value="${decision.projectName}"/></p>
            <h1 class="review-heading"><c:out value="${decision.title}"/></h1>
            <c:if test="${not empty decision.tags}">
                <ul class="tags">
                <c:forEach var="t" items="${decision.tags}">
                    <li class="tag tag-${t.color}"><c:out value="${t.name}"/></li>
                </c:forEach>
                </ul>
            </c:if>
          </div>
        </header>

        <section class="box">

            <div class="decision-field">
                <h2>Context</h2>
                <p class="prose"> <c:out value="${decision.context}"/> </p>
            </div>

            <div class="decision-field">
                <h2>Options</h2>
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
            </div>

            <div class="decision-field">
                <h2>Rationale</h2>
                <c:choose>
                    <c:when test="${not empty decision.rationale}">
                        <p class="prose"><c:out value="${decision.rationale}"/></p>
                    </c:when>

                    <c:otherwise>
                        <p class="muted">No rationale has been added.</p>
                    </c:otherwise>
                </c:choose>
            </div>

            <div class="decision-field">
                <h2>Status</h2>
                <p class="prose"><c:out value="${decision.status}"/></p>
                <p class="muted"><c:out value="${decision.decidedBy}"/>, <c:out value="${decision.dateAtTime(userZone)}"/></p>
            </div>
        </section>

        <section class="box project-decisions" aria-labelledby="decisionsHeading">
          <h2 id="decisionsHeading"><c:out value="${decision.projectName}"/> <span class="count"><c:out value="${fn:length(decisions)}"/></span></h2>
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

    </main>
</div>
</body>
</html>