<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="content-language" content="ja">
<meta charset="UTF-8">
<title>Admin Page</title>
</head>
<body>
<noscript><div style="color: red; background-color: yellow;">Javascript が無効です</div></noscript>
<c:if test="${ not empty message }">
<div style="background-color: #CEE2EA;">
<c:forEach var="msg" items="${ fn:split(message, System.lineSeparator()) }">
${ msg }<br>
</c:forEach>
</div>
</c:if>
<h2>API KEY</h2>
<form id="adminForm" action="/admin/AdminServlet" method="POST">
comsumerKey: <input type="text" name="comsumerKey" value="${ comsumerKey }"><br>
comsumerSecret: <input type="text" name="comsumerSecret" value="${ comsumerSecret }"><br>
accessToken: <input type="text" name="accessToken" value="${ accessToken }"><br>
tokenSecret: <input type="text" name="tokenSecret" value="${ tokenSecret }"><br>
<input type="button" value="Update" onclick="postAdminForm('update')">
<input type="button" value="Load" onclick="postAdminForm('load')">
<input type="hidden" name="func">
</form>
<h2>FRIENDS MANAGEMENT</h2>
<form id="adminForm" action="/admin/AdminServlet" method="POST">
<input type="button" value="Load Follower" onclick="postAdminForm('loadFollower')">
<input type="button" value="Load Following" onclick="postAdminForm('loadFollowing')">
<input type="button" value="Show Status" onclick="postAdminForm('showStatus')">
<input type="button" value="Update" onclick="postAdminForm('updateFriendship')">
<input type="hidden" name="func">
</form>
<h2>TASK MANAGEMENT</h2>
<form id="adminForm" action="/admin/AdminServlet" method="POST">
<input type="button" value="Init Task" onclick="postAdminForm('initTask')">
<input type="hidden" name="func">
</form>
<h3>Run Task</h3>
<form id="friendForm" action="/cron/FriendServlet" method="POST">
<input type="button" value="Run Task" onclick="postFriendForm()">
</form>
<hr>
<form id="adminForm" action="/admin/AdminServlet" method="POST">
<input type="button" value="Clear" onclick="postAdminForm('clear')">
<input type="hidden" name="func">
</form>
<script type="text/javascript">
  function postAdminForm(func) {
    var form = document.getElementById("adminForm");
    form.func.value = func;
    form.submit();
  }
  function postFriendForm(func) {
    var form = document.getElementById("friendForm");
    form.submit();
  }
</script>
</body>
</html>
