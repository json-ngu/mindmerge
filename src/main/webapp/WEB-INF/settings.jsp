<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="Settings | MindMerge"/>
<%@ include file="/WEB-INF/jspf/head.jspf" %>
<body>
<div class="app">
  <%@ include file="/WEB-INF/jspf/sidebar.jspf" %>

  <main class="app-main manage">
    <header class="manage-head">
      <h1>Settings</h1>
    </header>

    <%@ include file="/WEB-INF/jspf/notice.jspf" %>
    <c:if test="${not empty error}">
      <p class="alert" role="alert"><c:out value="${error}"/></p>
    </c:if>

    <c:if test="${empty error}">
      <section class="box" aria-labelledby="accountHeading">
        <h2 id="accountHeading">Account</h2>
        <dl class="facts">
          <div><dt>Name</dt><dd><c:out value="${sessionScope.user.fullName}"/></dd></div>
          <div><dt>Email</dt><dd><c:out value="${sessionScope.user.email}"/></dd></div>
          <div><dt>Companies</dt><dd><c:out value="${viewer.inCompany ? viewer.companies : 'None'}"/></dd></div>
        </dl>
        <form class="form" method="post" action="${pageContext.request.contextPath}/settings">
          <input type="hidden" name="action" value="updateName">
          <label for="fullName">Full name</label>
          <input id="fullName" name="fullName" type="text" required autocomplete="name"
                 maxlength="${fullNameLimit}" value="<c:out value='${fullName}'/>"
                 <c:if test="${not empty nameError}">aria-invalid="true" aria-describedby="nameError"</c:if>>
          <c:if test="${not empty nameError}">
            <p class="alert" id="nameError" role="alert"><c:out value="${nameError}"/></p>
          </c:if>
          <button class="pill pill-glow" type="submit">Save name</button>
        </form>
      </section>

      <section class="box" aria-labelledby="passwordHeading">
        <h2 id="passwordHeading">Change password</h2>
        <form class="form" method="post" action="${pageContext.request.contextPath}/settings">
          <input type="hidden" name="action" value="updatePassword">
          <label for="currentPassword">Current password</label>
          <input id="currentPassword" name="currentPassword" type="password" required autocomplete="current-password" maxlength="${passwordLimit}">
          <label for="newPassword">New password</label>
          <input id="newPassword" name="newPassword" type="password" required autocomplete="new-password" maxlength="${passwordLimit}">
          <label for="confirmPassword">Confirm new password</label>
          <input id="confirmPassword" name="confirmPassword" type="password" required autocomplete="new-password" maxlength="${passwordLimit}">
          <c:if test="${not empty passwordError}">
            <p class="alert" role="alert"><c:out value="${passwordError}"/></p>
          </c:if>
          <button class="pill pill-glow" type="submit">Change password</button>
        </form>
      </section>

      <section class="box" aria-labelledby="invitesHeading">
        <h2 id="invitesHeading">Invitations</h2>
        <form class="toggle-form" method="post" action="${pageContext.request.contextPath}/settings">
          <input type="hidden" name="action" value="updateInvites">
          <label class="toggle">
            <input type="checkbox" name="allowInvites" value="on" ${viewer.allowInvites ? 'checked' : ''} data-autosubmit>
            <span class="toggle-track" aria-hidden="true"></span>
            <span>
              <span class="toggle-label">Allow companies to invite me</span>
              <span class="panel-note">When this setting is disabled, companies cannot send you invitations.</span>
            </span>
          </label>
          <button class="pill pill-ghost pill-sm" type="submit">Save</button>
        </form>
      </section>

      <section class="box" aria-labelledby="companyHeading">
        <h2 id="companyHeading">Start a company</h2>
        <p class="panel-note">Create a company to invite members, organize teams and record decisions. You will be its owner.</p>
        <a class="pill pill-glow" href="${pageContext.request.contextPath}/companies/new">Create a company</a>
      </section>
    </c:if>
  </main>
</div>
<script src="${pageContext.request.contextPath}/js/manage.js"></script>
</body>
</html>
