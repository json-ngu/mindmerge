<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<c:set var="pageTitle" value="Dashboard | MindMerge"/>
<%@ include file="/WEB-INF/jspf/head.jspf" %>
<body>
<div class="app">
  <%@ include file="/WEB-INF/jspf/sidebar.jspf" %>

  <main class="app-main dash">
    <header class="dash-head">
      <div>
        <h1><c:out value="${greeting}"/><c:if test="${not empty viewer}">, <c:out value="${viewer.firstName}"/></c:if></h1>
        <c:if test="${not empty viewer}">
          <c:if test="${viewer.inCompany}">
            <p class="dash-sub"><c:out value="${viewer.companies}"/><c:if test="${not empty viewer.teams}">: <c:out value="${viewer.teams}"/></c:if></p>
          </c:if>
        </c:if>
      </div>
      <c:if test="${viewer.inCompany}">
      <form class="dash-search" action="${pageContext.request.contextPath}/dashboard" method="get" role="search">
        <label class="visually-hidden" for="q">Search decisions</label>
        <input id="q" name="q" type="search" value="<c:out value='${q}'/>" placeholder="Search decisions…" autocomplete="off">

        <%-- The selected tag is sent to the servlet as ?tag=database, for example. --%>
        <label class="visually-hidden" for="tag">Filter by tag</label>
        <select id="tag" name="tag" onchange="this.form.submit()">
          <option value="">All tags</option>
          <c:forEach var="t" items="${tags}">
            <option value="<c:out value='${t.name}'/>" ${tag eq t.name ? 'selected' : ''}>
              <c:out value="${t.name}"/>
            </option>
          </c:forEach>
        </select>

        <c:if test="${view eq 'mine'}"><input type="hidden" name="view" value="mine"></c:if>

        <%-- Remove the tag filter while keeping the search text and the All/My decisions view. --%>
        <c:if test="${not empty tag}">
          <c:url var="clearTagUrl" value="/dashboard">
            <c:if test="${not empty q}"><c:param name="q" value="${q}"/></c:if>
            <c:if test="${view eq 'mine'}"><c:param name="view" value="mine"/></c:if>
          </c:url>
          <a class="filter-clear" href="${clearTagUrl}">Clear tag</a>
        </c:if>
      </form>
      </c:if>
      <p class="visually-hidden" id="searchStatus" role="status" aria-live="polite"></p>
    </header>

    <%@ include file="/WEB-INF/jspf/notice.jspf" %>
    <c:if test="${not empty error}">
      <p class="alert" role="alert"><c:out value="${error}"/></p>
    </c:if>

    <c:if test="${empty error}">
      <%-- Pending invitations: the invited person accepts or declines here. --%>
      <c:forEach var="inv" items="${invites}">
        <section class="invite-banner" aria-label="Invitation from ${fn:escapeXml(inv.companyName)}">
          <p>
            <strong><c:out value="${inv.invitedByName}"/></strong> invited you to join
            <strong><c:out value="${inv.companyName}"/></strong> as
            <c:out value="${inv.role eq 'admin' ? 'an administrator' : 'a member'}"/>.
          </p>
          <form class="invite-actions" method="post" action="${pageContext.request.contextPath}/invites">
            <input type="hidden" name="invite" value="${inv.inviteId}">
            <input type="hidden" name="companyName" value="<c:out value='${inv.companyName}'/>">
            <button class="pill pill-glow pill-sm" type="submit" name="action" value="accept">Accept</button>
            <button class="pill pill-ghost pill-sm" type="submit" name="action" value="decline">Decline</button>
          </form>
        </section>
      </c:forEach>
    </c:if>

    <c:if test="${empty error and not viewer.inCompany}">
      <section class="no-company">
        <h2>You are not a member of any company</h2>
        <p>
          Create a company to begin recording decisions with your team, or ask a company administrator to invite you.
          <c:choose>
            <c:when test="${viewer.allowInvites}">Invitations you receive will appear here.</c:when>
            <c:otherwise>Invitations are disabled. You can enable them in <a href="${pageContext.request.contextPath}/settings">Settings</a>.</c:otherwise>
          </c:choose>
        </p>
        <a class="pill pill-glow" href="${pageContext.request.contextPath}/companies/new">Create a company</a>
      </section>
    </c:if>

    <c:if test="${empty error and viewer.inCompany}">
      <%-- Proposed decisions on teams the viewer leads or approves for. Members cannot approve,
           so they never see this section. --%>
      <c:if test="${viewer.reviewer}">
      <section class="dash-review" aria-labelledby="reviewHeading">
        <h2 id="reviewHeading">Awaiting your approval</h2>
        <c:choose>
          <c:when test="${empty review}">
            <p class="dash-empty">No decisions are awaiting your approval. Proposed decisions on teams where you are a lead or approver will appear here.</p>
          </c:when>
          <c:otherwise>
            <ul class="review-list">
              <c:forEach var="d" items="${review}">
                <li class="review-card">
                  <p class="feed-project"><c:out value="${d.projectName}"/></p>
                  <p class="review-title"><a href="${pageContext.request.contextPath}/approvals?decision=${d.decisionId}"><c:out value="${d.title}"/></a></p>
                  <p class="feed-meta"><span><c:out value="${d.decidedBy}"/></span><span>Proposed on <c:out value="${d.dateAtTime(userZone)}"/></span></p>
                </li>
              </c:forEach>
            </ul>
          </c:otherwise>
        </c:choose>
      </section>
      </c:if>

      <%-- The viewer's own proposals still waiting for a lead or approver; once approved they
           leave this list and show as Decided in the feed. --%>
      <c:if test="${not empty inReview}">
        <section class="box in-review" aria-labelledby="inReviewHeading">
          <h2 id="inReviewHeading">Your decisions in review <span class="count"><c:out value="${fn:length(inReview)}"/></span></h2>
          <ul class="row-list">
            <c:forEach var="d" items="${inReview}">
              <li class="row">
                <div>
                  <p class="row-meta"><c:out value="${d.projectName}"/></p>
                  <p class="row-title"><c:out value="${d.title}"/></p>
                  <p class="row-meta">Proposed on <c:out value="${d.dateAtTime(userZone)}"/></p>
                </div>
                <span class="status status-proposed">Awaiting approval</span>
              </li>
            </c:forEach>
          </ul>
        </section>
      </c:if>

      <div class="dash-grid">
        <section class="dash-feed" id="feed" aria-labelledby="feedHeading">
          <div class="section-bar">
            <h2 id="feedHeading">
              <c:choose>
                <c:when test="${not empty q and not empty tag}">Results for “<c:out value="${q}"/>” in “<c:out value="${tag}"/>”</c:when>
                <c:when test="${not empty q}">Results for “<c:out value="${q}"/>”</c:when>
                <c:when test="${not empty tag}">Tagged “<c:out value="${tag}"/>”</c:when>
                <c:otherwise>Recent decisions</c:otherwise>
              </c:choose>
              <c:if test="${not empty q or not empty tag}"> <span class="muted"><c:out value="${fn:length(feed)}"/> <c:out value="${fn:length(feed) == 1 ? 'decision' : 'decisions'}"/></span></c:if>
            </h2>
            <nav class="segmented" aria-label="Decision filter">
              <%-- Preserve both filters when switching between all decisions and the viewer's own. --%>
              <c:url var="teamUrl" value="/dashboard">
                <c:if test="${not empty q}"><c:param name="q" value="${q}"/></c:if>
                <c:if test="${not empty tag}"><c:param name="tag" value="${tag}"/></c:if>
              </c:url>
              <c:url var="mineUrl" value="/dashboard">
                <c:param name="view" value="mine"/>
                <c:if test="${not empty q}"><c:param name="q" value="${q}"/></c:if>
                <c:if test="${not empty tag}"><c:param name="tag" value="${tag}"/></c:if>
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
        </section>

        <aside class="dash-projects" aria-labelledby="projectsHeading">
          <h2 id="projectsHeading">Your projects</h2>
          <c:choose>
            <c:when test="${empty projects}">
              <p class="dash-empty">You are not a member of any project team.</p>
            </c:when>
            <c:otherwise>
              <ul class="project-list">
                <c:forEach var="p" items="${projects}">
                  <li class="project-row">
                    <div>
                      <p class="project-name"><a href="${pageContext.request.contextPath}/projects?project=${p.projectId}"><c:out value="${p.name}"/></a></p>
                      <p class="project-meta">
                        <span class="project-status project-${p.status}"><c:out value="${p.status eq 'on_hold' ? 'on hold' : p.status}"/></span>
                        <span><c:out value="${p.teamName}"/></span>
                      </p>
                    </div>
                    <p class="project-count" title="Decisions recorded">
                      <c:out value="${p.decisionCount}"/>
                      <span><c:out value="${p.decisionCount == 1 ? 'decision' : 'decisions'}"/></span>
                    </p>
                  </li>
                </c:forEach>
              </ul>
            </c:otherwise>
          </c:choose>
        </aside>
      </div>
    </c:if>
  </main>
</div>

<script src="${pageContext.request.contextPath}/js/dashboard.js"></script>
</body>
</html>