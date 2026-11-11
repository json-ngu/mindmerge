<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="Comments | MindMerge"/>
<%@ include file="/WEB-INF/jspf/head.jspf" %>
<body>
<div class="app">
  <%@ include file="/WEB-INF/jspf/sidebar.jspf" %>
  <main class="app-main manage">
    <p class="back-link"><a href="${pageContext.request.contextPath}/dashboard">Back to dashboard</a></p>
    <c:choose>
      <c:when test="${not empty error}">
        <p class="alert" role="alert"><c:out value="${error}"/></p>
      </c:when>
      <c:otherwise>
        <header class="manage-head">
          <div>
            <h1 class="review-heading">Decision discussion</h1>
            <p class="dash-sub"><c:out value="${discussionTitle}"/></p>
          </div>
        </header>
        <%@ include file="/WEB-INF/jspf/comments.jspf" %>
      </c:otherwise>
    </c:choose>
  </main>
</div>
</body>
</html>
