<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<c:set var="pageTitle" value="Teams | MindMerge"/>
<c:set var="pickerAction" value="/teams"/>
<%@ include file="/WEB-INF/jspf/head.jspf" %>
<body>
<div class="app">
  <%@ include file="/WEB-INF/jspf/sidebar.jspf" %>

  <main class="app-main manage">
    <header class="manage-head">
      <div>
        <h1>Teams</h1>
        <p class="dash-sub"><c:out value="${company.name}"/></p>
      </div>
      <%@ include file="/WEB-INF/jspf/company-picker.jspf" %>
    </header>

    <%@ include file="/WEB-INF/jspf/notice.jspf" %>
    <c:if test="${not empty error}">
      <p class="alert" role="alert"><c:out value="${error}"/></p>
    </c:if>

    <c:if test="${empty error}">
      <section class="box" aria-labelledby="newTeamHeading">
        <h2 id="newTeamHeading">Create a team</h2>
        <form class="inline-form" method="post" action="${pageContext.request.contextPath}/teams">
          <input type="hidden" name="company" value="${company.companyId}">
          <input type="hidden" name="action" value="create">
          <label class="visually-hidden" for="teamName">Team name</label>
          <input id="teamName" class="input" name="name" type="text" required maxlength="150" placeholder="For example: Platform Engineering">
          <button class="pill pill-glow" type="submit">Create team</button>
        </form>
      </section>

      <c:choose>
        <c:when test="${empty teams}">
          <section class="box" aria-labelledby="noTeamsHeading">
            <h2 id="noTeamsHeading">Teams <span class="count">0</span></h2>
            <p class="panel-note">No teams have been created. Create a team above, then add members of <c:out value="${company.name}"/>.</p>
          </section>
        </c:when>
        <c:otherwise>
          <div class="team-grid">
            <c:forEach var="t" items="${teams}">
              <section class="box team-card" aria-labelledby="team${t.teamId}">
                <h2 id="team${t.teamId}"><c:out value="${t.name}"/> <span class="count"><c:out value="${fn:length(t.members)}"/></span></h2>

                <c:choose>
                  <c:when test="${empty t.members}">
                    <p class="dash-empty">This team has no members.</p>
                  </c:when>
                  <c:otherwise>
                    <ul class="row-list">
                      <c:forEach var="m" items="${t.members}">
                        <li class="row">
                          <p class="row-title"><c:out value="${m.fullName}"/></p>
                          <div class="row-actions">
                            <form class="role-form" method="post" action="${pageContext.request.contextPath}/teams">
                              <input type="hidden" name="company" value="${company.companyId}">
                              <input type="hidden" name="team" value="${t.teamId}">
                              <input type="hidden" name="action" value="role">
                              <input type="hidden" name="user" value="${m.userId}">
                              <label class="visually-hidden" for="tr${t.teamId}-${m.userId}">Team role for <c:out value="${m.fullName}"/></label>
                              <select id="tr${t.teamId}-${m.userId}" class="select select-sm" name="role" data-autosubmit>
                                <option value="lead" ${m.role eq 'lead' ? 'selected' : ''}>Lead</option>
                                <option value="approver" ${m.role eq 'approver' ? 'selected' : ''}>Approver</option>
                                <option value="member" ${m.role eq 'member' ? 'selected' : ''}>Member</option>
                              </select>
                              <button class="pill pill-ghost pill-sm" type="submit">Save</button>
                            </form>
                            <form method="post" action="${pageContext.request.contextPath}/teams"
                                  data-confirm="Remove ${fn:escapeXml(m.fullName)} from ${fn:escapeXml(t.name)}? Their decisions and comments will be retained.">
                              <input type="hidden" name="company" value="${company.companyId}">
                              <input type="hidden" name="team" value="${t.teamId}">
                              <input type="hidden" name="action" value="remove">
                              <input type="hidden" name="user" value="${m.userId}">
                              <button class="link-danger" type="submit">Remove</button>
                            </form>
                          </div>
                        </li>
                      </c:forEach>
                    </ul>
                  </c:otherwise>
                </c:choose>

                <%-- Company members who aren't on this team yet. --%>
                <c:set var="addable" value="0"/>
                <c:forEach var="m" items="${members}"><c:if test="${not t.hasMember(m.userId)}"><c:set var="addable" value="${addable + 1}"/></c:if></c:forEach>
                <c:if test="${addable > 0}">
                  <form class="inline-form add-member" method="post" action="${pageContext.request.contextPath}/teams">
                    <input type="hidden" name="company" value="${company.companyId}">
                    <input type="hidden" name="team" value="${t.teamId}">
                    <input type="hidden" name="action" value="add">
                    <label class="visually-hidden" for="add${t.teamId}">Member to add</label>
                    <select id="add${t.teamId}" class="select select-sm" name="user" required>
                      <option value="">Select a member…</option>
                      <c:forEach var="m" items="${members}">
                        <c:if test="${not t.hasMember(m.userId)}"><option value="${m.userId}"><c:out value="${m.fullName}"/></option></c:if>
                      </c:forEach>
                    </select>
                    <label class="visually-hidden" for="addRole${t.teamId}">Team role</label>
                    <select id="addRole${t.teamId}" class="select select-sm" name="role">
                      <option value="member">Member</option>
                      <option value="approver">Approver</option>
                      <option value="lead">Lead</option>
                    </select>
                    <button class="pill pill-ghost pill-sm" type="submit">Add</button>
                  </form>
                </c:if>

                <form class="inline-form team-rename" method="post" action="${pageContext.request.contextPath}/teams">
                  <input type="hidden" name="company" value="${company.companyId}">
                  <input type="hidden" name="team" value="${t.teamId}">
                  <input type="hidden" name="action" value="rename">
                  <label class="visually-hidden" for="rename${t.teamId}">New name for <c:out value="${t.name}"/></label>
                  <input id="rename${t.teamId}" class="input" name="name" type="text" required maxlength="150" value="<c:out value='${t.name}'/>">
                  <button class="pill pill-ghost pill-sm" type="submit">Rename</button>
                </form>
              </section>
            </c:forEach>
          </div>
        </c:otherwise>
      </c:choose>
    </c:if>
  </main>
</div>
<script src="${pageContext.request.contextPath}/js/manage.js"></script>
</body>
</html>
