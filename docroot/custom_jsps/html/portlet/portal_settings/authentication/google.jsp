<%--
/**
 * Copyright (c) 2000-2013 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
--%>

<%@ include file="/html/portlet/portal_settings/init.jsp" %>

<%
boolean googleAuthEnabled = PrefsPropsUtil.getBoolean(company.getCompanyId(), "google.auth.enabled", true);
String googleClientId = PrefsPropsUtil.getString(company.getCompanyId(), "google.client.id");
String googleClientSecret = PrefsPropsUtil.getString(company.getCompanyId(), "google.client.secret");
%>

<aui:fieldset>
	<aui:input label="enabled" name='<%= "settings--google.auth.enabled--" %>' type="checkbox" value="<%= googleAuthEnabled %>" />

	<div class="alert alert-info">
		<liferay-ui:message key="you-can-introduce-the-credentials-in-the-following-fields-or-leave-them-blank-to-obtain-them-from-client_secrets.json-file" />
	</div>

	<aui:input cssClass="lfr-input-text-container" label="google-client-id" name='<%= "settings--google.client.id--" %>' type="text" value="<%= googleClientId %>" />

	<aui:input cssClass="lfr-input-text-container" label="google-client-secret" name='<%= "settings--google.client.secret--" %>' type="text" value="<%= googleClientSecret %>" />
</aui:fieldset>