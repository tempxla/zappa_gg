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
<form action="/admin/AdminServlet" method="POST">
comsumerKey: <input type="text" name="comsumerKey" value="${ comsumerKey }"><br>
comsumerSecret: <input type="text" name="comsumerSecret" value="${ comsumerSecret }"><br>
accessToken: <input type="text" name="accessToken" value="${ accessToken }"><br>
tokenSecret: <input type="text" name="tokenSecret" value="${ tokenSecret }"><br>
<input type="button" value="Update" onclick="doPost(this.parentNode, 'update')">
<input type="button" value="Load" onclick="doPost(this.parentNode, 'load')">
<input type="hidden" name="func">
</form>
<h2>FRIENDS MANAGEMENT</h2>
<form action="/admin/AdminServlet" method="POST">
<input type="button" value="Load Follower" onclick="doPost(this.parentNode, 'loadFollower')">
<input type="button" value="Load Following" onclick="doPost(this.parentNode, 'loadFollowing')">
<input type="button" value="Show Status" onclick="doPost(this.parentNode, 'showStatus')">
<input type="button" value="Detect Unfollow" onclick="doPost(this.parentNode, 'updateUnfollow')">
<input type="button" value="Update Friendship" onclick="doPost(this.parentNode, 'updateFriendship')">
<input type="hidden" name="func">
</form>
<h2>TASK MANAGEMENT</h2>
<form action="/admin/AdminServlet" method="POST">
<input type="button" value="Init Task" onclick="doPost(this.parentNode, 'initTask')">
<input type="hidden" name="func">
</form>
<h3>Run Task</h3>
<form action="/cron/FriendServlet" method="POST">
<input type="button" value="Run Task" onclick="doPost(this,parentNode)">
<input type="hidden" name="func">
</form>
<hr>
<form action="/admin/AdminServlet" method="POST">
<input type="button" value="Clear" onclick="doPost(this.parentNode, 'clear')">
<input type="button" value="Debug" onclick="doPost(this.parentNode, 'debug')">
<input type="button" value="Debug2" onclick="doPost(this.parentNode, 'debug2')">
<input type="text" name="tw_ids">
<input type="button" value="Debug3" onclick="doPost(this.parentNode, 'debug3')">
<input type="hidden" name="func">
</form>
<script type="text/javascript">
  function doPost(frm, func) {
    frm.func.value = func;
    frm.submit();
  }
</script>
</body>
</html>
