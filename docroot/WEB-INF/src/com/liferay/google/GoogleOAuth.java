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

package com.liferay.google;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import javax.portlet.PortletMode;
import javax.portlet.PortletRequest;
import javax.portlet.PortletURL;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Userinfo;
import com.liferay.portal.kernel.deploy.DeployManagerUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.portlet.LiferayWindowState;
import com.liferay.portal.kernel.struts.BaseStrutsAction;
import com.liferay.portal.kernel.util.CalendarFactoryUtil;
import com.liferay.portal.kernel.util.Constants;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.PrefsPropsUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.model.Contact;
import com.liferay.portal.model.User;
import com.liferay.portal.model.UserGroupRole;
import com.liferay.portal.service.GroupLocalServiceUtil;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.service.UserLocalServiceUtil;
import com.liferay.portal.theme.ThemeDisplay;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portal.util.PortletKeys;
import com.liferay.portlet.PortletURLFactoryUtil;
import com.liferay.portlet.expando.model.ExpandoTableConstants;
import com.liferay.portlet.expando.service.ExpandoValueLocalServiceUtil;

/**
 * @author Sergio González
 */
public class GoogleOAuth extends BaseStrutsAction {

	public static final String GOOGLE_ACCESS_TOKEN = "googleAccessToken";

	public static final String GOOGLE_REFRESH_TOKEN = "googleRefreshToken";

	public static final String GOOGLE_USER_ID = "googleUserId";
	
	public static final String GOOGLE_SITE_CLIENT_ID = "googleSiteClientId";
	
	public static final String GOOGLE_SITE_CLIENT_SECRET = "googleSiteClientSecret";

	public String execute(
			HttpServletRequest request, HttpServletResponse response)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)request.getAttribute(
			WebKeys.THEME_DISPLAY);

		String cmd = ParamUtil.getString(request, Constants.CMD);

		String redirectUri = PortalUtil.getPortalURL(request) + _REDIRECT_URI;

		if (cmd.equals("login")) {
			GoogleAuthorizationCodeFlow flow = getFlow(
				themeDisplay.getCompanyId(), themeDisplay.getScopeGroupId());

			GoogleAuthorizationCodeRequestUrl
				googleAuthorizationCodeRequestUrl = flow.newAuthorizationUrl();

			googleAuthorizationCodeRequestUrl.setRedirectUri(redirectUri);

			String url = googleAuthorizationCodeRequestUrl.build();

			response.sendRedirect(url);
		}
		else if (cmd.equals("token")) {
			HttpSession session = request.getSession();

			String code = ParamUtil.getString(request, "code");

			if (Validator.isNotNull(code)) {
				Credential credential = exchangeCode(
					themeDisplay.getCompanyId(), themeDisplay.getScopeGroupId(), code, redirectUri);

				User user = setGoogleCredentials(
					session, themeDisplay.getCompanyId(), credential);

				if ((user != null) &&
					(user.getStatus() == WorkflowConstants.STATUS_INCOMPLETE)) {

					redirectUpdateAccount(request, response, user);

					return null;
				}

				sendLoginRedirect(request, response);

				return null;
			}

			String error = ParamUtil.getString(request, "error");

			if (error.equals("access_denied")) {
				sendLoginRedirect(request, response);

				return null;
			}
		}

		return null;
	}

	protected User addUser(
			HttpSession session, long companyId, Userinfo userinfo)
		throws Exception {

		long creatorUserId = 0;
		boolean autoPassword = true;
		String password1 = StringPool.BLANK;
		String password2 = StringPool.BLANK;
		boolean autoScreenName = true;
		String screenName = StringPool.BLANK;
		String emailAddress = userinfo.getEmail();
		String openId = StringPool.BLANK;
		Locale locale = LocaleUtil.getDefault();
		String firstName = userinfo.getGivenName();
		String middleName = StringPool.BLANK;
		String lastName = userinfo.getFamilyName();
		int prefixId = 0;
		int suffixId = 0;
		boolean male = Validator.equals(userinfo.getGender(), "male");
		int birthdayMonth = Calendar.JANUARY;
		int birthdayDay = 1;
		int birthdayYear = 1970;
		String jobTitle = StringPool.BLANK;
		long[] groupIds = null;
		long[] organizationIds = null;
		long[] roleIds = null;
		long[] userGroupIds = null;
		boolean sendEmail = true;

		ServiceContext serviceContext = new ServiceContext();

		User user = UserLocalServiceUtil.addUser(
			creatorUserId, companyId, autoPassword, password1, password2,
			autoScreenName, screenName, emailAddress, 0, openId, locale,
			firstName, middleName, lastName, prefixId, suffixId, male,
			birthdayMonth, birthdayDay, birthdayYear, jobTitle, groupIds,
			organizationIds, roleIds, userGroupIds, sendEmail, serviceContext);

		user = UserLocalServiceUtil.updateLastLogin(
			user.getUserId(), user.getLoginIP());

		user = UserLocalServiceUtil.updatePasswordReset(
			user.getUserId(), false);

		user = UserLocalServiceUtil.updateEmailAddressVerified(
			user.getUserId(), true);

		session.setAttribute("GOOGLE_USER_EMAIL_ADDRESS", emailAddress);

		return user;
	}

	protected Credential exchangeCode(
			long companyId, long scopeGroupId, String authorizationCode, String redirectUri)
		throws SystemException, PortalException {

		try {
			GoogleAuthorizationCodeFlow flow = getFlow(companyId, scopeGroupId);

			GoogleAuthorizationCodeTokenRequest token = flow.newTokenRequest(
				authorizationCode);

			token.setRedirectUri(redirectUri);

			GoogleTokenResponse response = token.execute();

			return flow.createAndStoreCredential(response, null);
		}
		catch (IOException e) {
			System.err.println("An error occurred: " + e);

			throw new CodeExchangeException();
		}
	}

	protected GoogleAuthorizationCodeFlow getFlow(long companyId, long scopeGroupId)
		throws IOException, SystemException, PortalException {

		HttpTransport httpTransport = new NetHttpTransport();
		JacksonFactory jsonFactory = new JacksonFactory();

		GoogleAuthorizationCodeFlow.Builder builder = null;
		
		List<String> scopes = null;

		if (DeployManagerUtil.isDeployed(_GOOGLE_DRIVE_CONTEXT)) {
			scopes = _SCOPES_DRIVE;
		}
		else {
			scopes = _SCOPES_LOGIN;
		}

		// Try to get Google Client ID and Client Secret for current Site settings
		
		String googleClientId = (String)GroupLocalServiceUtil.getGroup(scopeGroupId)
				.getExpandoBridge().getAttribute(GOOGLE_SITE_CLIENT_ID, false);
		String googleClientSecret = (String)GroupLocalServiceUtil.getGroup(scopeGroupId)
				.getExpandoBridge().getAttribute(GOOGLE_SITE_CLIENT_SECRET, false);
		
		// If they are not set, then try to get it from Portal settings
		
		if (Validator.isNull(googleClientId) ||
			Validator.isNull(googleClientSecret)) {
			
			_log.debug("Client ID and Client Secret was not set for Site, "
					+ "getting from Portal Settings");
			
			googleClientId = PrefsPropsUtil.getString(
				companyId, "google.client.id");
			googleClientSecret = PrefsPropsUtil.getString(
				companyId, "google.client.secret");
		}

		// If they are not set both for Site and Portal settings, then
		// read it from client_secrets.json
		
		if (Validator.isNull(googleClientId) ||
			Validator.isNull(googleClientSecret)) {
			
			_log.debug("Client ID and Client Secret was not set for Portal, "
					+ "reading JSON code from plugin resources");

			InputStream is = GoogleOAuth.class.getResourceAsStream(
				_CLIENT_SECRETS_LOCATION);

			GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
				jsonFactory, new InputStreamReader(is));

			builder = new GoogleAuthorizationCodeFlow.Builder(
				httpTransport, jsonFactory, clientSecrets, scopes);
		}
		else {
			builder = new GoogleAuthorizationCodeFlow.Builder(
				httpTransport, jsonFactory, googleClientId, googleClientSecret,
				scopes);
		}

		String accessType = "online";

		if (DeployManagerUtil.isDeployed(_GOOGLE_DRIVE_CONTEXT)) {
			accessType = "offline";
		}

		builder.setAccessType(accessType);
		builder.setApprovalPrompt("auto");

		return builder.build();
	}

	protected Userinfo getUserInfo(Credential credentials)
		throws NoSuchUserIdException {

		Oauth2.Builder builder = new Oauth2.Builder(
			new NetHttpTransport(), new JacksonFactory(), credentials);

		Oauth2 oauth2 = builder.build();

		Userinfo userInfo = null;

		try {
			userInfo = oauth2.userinfo().get().execute();
		}
		catch (IOException e) {
			System.err.println("An error occurred: " + e);
		}

		if ((userInfo != null) && (userInfo.getId() != null)) {
			return userInfo;
		}
		else {
			throw new NoSuchUserIdException();
		}
	}

	protected void redirectUpdateAccount(
			HttpServletRequest request, HttpServletResponse response, User user)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)request.getAttribute(
			WebKeys.THEME_DISPLAY);

		PortletURL portletURL = PortletURLFactoryUtil.create(
			request, PortletKeys.LOGIN, themeDisplay.getPlid(),
			PortletRequest.RENDER_PHASE);

		portletURL.setParameter("saveLastPath", Boolean.FALSE.toString());
		portletURL.setParameter("struts_action", "/login/update_account");

		PortletURL redirectURL = PortletURLFactoryUtil.create(
			request, PortletKeys.FAST_LOGIN, themeDisplay.getPlid(),
			PortletRequest.RENDER_PHASE);

		redirectURL.setParameter("struts_action", "/login/login_redirect");
		redirectURL.setParameter("emailAddress", user.getEmailAddress());
		redirectURL.setParameter("anonymousUser", Boolean.FALSE.toString());
		redirectURL.setPortletMode(PortletMode.VIEW);
		redirectURL.setWindowState(LiferayWindowState.POP_UP);

		portletURL.setParameter("redirect", redirectURL.toString());
		portletURL.setParameter("userId", String.valueOf(user.getUserId()));
		portletURL.setParameter("emailAddress", user.getEmailAddress());
		portletURL.setParameter("firstName", user.getFirstName());
		portletURL.setParameter("lastName", user.getLastName());
		portletURL.setPortletMode(PortletMode.VIEW);
		portletURL.setWindowState(LiferayWindowState.POP_UP);

		response.sendRedirect(portletURL.toString());
	}

	protected void sendLoginRedirect(
			HttpServletRequest request, HttpServletResponse response)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)request.getAttribute(
			WebKeys.THEME_DISPLAY);

		PortletURL portletURL = PortletURLFactoryUtil.create(
			request, PortletKeys.FAST_LOGIN, themeDisplay.getPlid(),
			PortletRequest.RENDER_PHASE);

		portletURL.setWindowState(LiferayWindowState.POP_UP);

		portletURL.setParameter("struts_action", "/login/login_redirect");

		response.sendRedirect(portletURL.toString());
	}

	protected User setGoogleCredentials(
			HttpSession session, long companyId, Credential credential)
		throws Exception {

		Userinfo userinfo = getUserInfo(credential);

		if (userinfo == null) {
			return null;
		}

		User user = null;

		String emailAddress = userinfo.getEmail();

		if ((user == null) && Validator.isNotNull(emailAddress)) {
			user = UserLocalServiceUtil.fetchUserByEmailAddress(
				companyId, emailAddress);

			if ((user != null) &&
				(user.getStatus() != WorkflowConstants.STATUS_INCOMPLETE)) {

				session.setAttribute("GOOGLE_USER_EMAIL_ADDRESS", emailAddress);
			}
		}

		if (user != null) {
			if (user.getStatus() == WorkflowConstants.STATUS_INCOMPLETE) {
				session.setAttribute(
					"GOOGLE_INCOMPLETE_USER_ID", userinfo.getId());

				user.setEmailAddress(userinfo.getEmail());
				user.setFirstName(userinfo.getGivenName());
				user.setLastName(userinfo.getFamilyName());

				return user;
			}

			user = updateUser(user, userinfo);
		}
		else {
			user = addUser(session, companyId, userinfo);
		}

		if (DeployManagerUtil.isDeployed(_GOOGLE_DRIVE_CONTEXT)) {
			updateCustomFields(
				user, userinfo, credential.getAccessToken(),
				credential.getRefreshToken());
		}

		return user;
	}

	protected void updateCustomFields(
			User user, Userinfo userinfo, String accessToken,
			String refreshToken)
		throws PortalException, SystemException {

		ExpandoValueLocalServiceUtil.addValue(
			user.getCompanyId(), User.class.getName(),
			ExpandoTableConstants.DEFAULT_TABLE_NAME, GOOGLE_ACCESS_TOKEN,
			user.getUserId(), accessToken);

		ExpandoValueLocalServiceUtil.addValue(
			user.getCompanyId(), User.class.getName(),
			ExpandoTableConstants.DEFAULT_TABLE_NAME, GOOGLE_REFRESH_TOKEN,
			user.getUserId(), refreshToken);

		ExpandoValueLocalServiceUtil.addValue(
			user.getCompanyId(), User.class.getName(),
			ExpandoTableConstants.DEFAULT_TABLE_NAME, GOOGLE_USER_ID,
			user.getUserId(), userinfo.getId());
	}

	protected User updateUser(User user, Userinfo userinfo) throws Exception {
		String emailAddress = userinfo.getEmail();
		String firstName = userinfo.getGivenName();
		String lastName = userinfo.getFamilyName();
		boolean male = Validator.equals(userinfo.getGender(), "male");

		if (emailAddress.equals(user.getEmailAddress()) &&
			firstName.equals(user.getFirstName()) &&
			lastName.equals(user.getLastName()) && (male == user.isMale())) {

			return user;
		}

		Contact contact = user.getContact();

		Calendar birthdayCal = CalendarFactoryUtil.getCalendar();

		birthdayCal.setTime(contact.getBirthday());

		int birthdayMonth = birthdayCal.get(Calendar.MONTH);
		int birthdayDay = birthdayCal.get(Calendar.DAY_OF_MONTH);
		int birthdayYear = birthdayCal.get(Calendar.YEAR);

		long[] groupIds = null;
		long[] organizationIds = null;
		long[] roleIds = null;
		List<UserGroupRole> userGroupRoles = null;
		long[] userGroupIds = null;

		ServiceContext serviceContext = new ServiceContext();

		if (!StringUtil.equalsIgnoreCase(
				emailAddress, user.getEmailAddress())) {

			UserLocalServiceUtil.updateEmailAddress(
				user.getUserId(), StringPool.BLANK, emailAddress, emailAddress);
		}

		UserLocalServiceUtil.updateEmailAddressVerified(user.getUserId(), true);

		return UserLocalServiceUtil.updateUser(
			user.getUserId(), StringPool.BLANK, StringPool.BLANK,
			StringPool.BLANK, false, user.getReminderQueryQuestion(),
			user.getReminderQueryAnswer(), user.getScreenName(), emailAddress,
			0, user.getOpenId(), user.getLanguageId(), user.getTimeZoneId(),
			user.getGreeting(), user.getComments(), firstName,
			user.getMiddleName(), lastName, contact.getPrefixId(),
			contact.getSuffixId(), male, birthdayMonth, birthdayDay,
			birthdayYear, contact.getSmsSn(), contact.getAimSn(),
			contact.getFacebookSn(), contact.getIcqSn(), contact.getJabberSn(),
			contact.getMsnSn(), contact.getMySpaceSn(), contact.getSkypeSn(),
			contact.getTwitterSn(), contact.getYmSn(), contact.getJobTitle(),
			groupIds, organizationIds, roleIds, userGroupRoles, userGroupIds,
			serviceContext);
	}

	private static final String _CLIENT_SECRETS_LOCATION =
		"client_secrets.json";

	private static final String _GOOGLE_DRIVE_CONTEXT = "google-drive-hook";

	private static final String _REDIRECT_URI =
		"/c/portal/google_login?cmd=token";

	private static final List<String> _SCOPES_DRIVE = Arrays.asList(
		"https://www.googleapis.com/auth/userinfo.email",
		"https://www.googleapis.com/auth/userinfo.profile",
		"https://www.googleapis.com/auth/drive");

	private static final List<String> _SCOPES_LOGIN = Arrays.asList(
		"https://www.googleapis.com/auth/userinfo.email",
		"https://www.googleapis.com/auth/userinfo.profile");
	
	private static Log _log = LogFactoryUtil.getLog(GoogleOAuth.class);

}