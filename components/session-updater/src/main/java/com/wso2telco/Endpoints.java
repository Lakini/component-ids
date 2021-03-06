/*******************************************************************************
 * Copyright (c) 2015-2016, WSO2.Telco Inc. (http://www.wso2telco.com) 
 *
 * All Rights Reserved. WSO2.Telco Inc. licences this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.wso2telco;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wso2telco.core.config.MIFEAuthentication;
import com.wso2telco.core.config.model.MobileConnectConfig;
import com.wso2telco.core.config.model.PinConfig;
import com.wso2telco.core.config.service.ConfigurationService;
import com.wso2telco.core.config.service.ConfigurationServiceImpl;
import com.wso2telco.core.config.util.PinConfigUtil;
import com.wso2telco.cryptosystem.AESencrp;
import com.wso2telco.dbUtil.DataBaseConnectUtils;
import com.wso2telco.entity.*;
import com.wso2telco.exception.AuthenticatorException;
import com.wso2telco.exception.CommonAuthenticatorException;
import com.wso2telco.ids.datapublisher.model.UserStatus;
import com.wso2telco.ids.datapublisher.util.DataPublisherUtil;
import com.wso2telco.model.backchannel.BackChannelRequestDetails;
import com.wso2telco.model.backchannel.BackChannelTokenResponse;
import com.wso2telco.util.Constants;
import com.wso2telco.util.DbUtil;
import org.apache.axis2.AxisFault;
import org.apache.commons.lang.IncompleteArgumentException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.carbon.authenticator.stub.LoginAuthenticationExceptionException;
import org.wso2.carbon.identity.application.authentication.framework.cache.AuthenticationContextCache;
import org.wso2.carbon.identity.application.authentication.framework.cache.AuthenticationContextCacheEntry;
import org.wso2.carbon.identity.application.authentication.framework.cache.AuthenticationContextCacheKey;
import org.wso2.carbon.identity.application.authentication.framework.context.AuthenticationContext;
import org.wso2.carbon.identity.application.authentication.framework.exception.AuthenticationFailedException;
import org.wso2.carbon.identity.mgt.stub.UserIdentityManagementAdminServiceIdentityMgtServiceExceptionException;
import org.wso2.carbon.um.ws.api.stub.RemoteUserStoreManagerServiceUserStoreExceptionException;

import javax.naming.ConfigurationException;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.rmi.RemoteException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * The Class Endpoints.
 */
@Path("/endpoint")
public class Endpoints {

    private static MobileConnectConfig mobileConnectConfigs = null;

    private static boolean temp = false;

    /**
     * The context.
     */
    //private static final Logger LOG = Logger.getLogger(Endpoints.class.getName());
    @Context
    private UriInfo context;

    /**
     * The success response.
     */
    String successResponse = "\"" + "amountTransaction" + "\"";

    /**
     * The service exception.
     */
    String serviceException = "\"" + "serviceException" + "\"";

    /**
     * The policy exception.
     */
    String policyException = "\"" + "policyException" + "\"";

    /**
     * The error return.
     */
    String errorReturn = "\"" + "errorreturn" + "\"";

    /**
     * The log.
     */
    private static Log log = LogFactory.getLog(Endpoints.class);

    /**
     * The ussd no of attempts.
     */
    private static Map<String, Integer> ussdNoOfAttempts = new HashMap<String, Integer>();

    /**
     * constant for the first attempt in LOA3 flow
     */
    private static final int FIRST_ATTEMPT = 1;

    /**
     * The Configuration service
     */
    private static ConfigurationService configurationService = new ConfigurationServiceImpl();

    /**
     * Instantiates a new endpoints.
     */
    public Endpoints() {

    }

    static {
        mobileConnectConfigs = configurationService.getDataHolder().getMobileConnectConfig();
    }

    @POST
    @Path("/update/saa/status")
    @Consumes("application/json")
    @Produces("application/json")
    public Response saaUpdateStatus(String jsonBody) {
        log.info("Received SAA update status request " + jsonBody);

        SaaStatusRequest saaStatusRequest = new Gson().fromJson(jsonBody, SaaStatusRequest.class);
        SaaResponse saaResponse;
        Response response;

        try {
            DatabaseUtils.updateStatus(saaStatusRequest.getSessionDataKey(), saaStatusRequest.getStatus());

            saaResponse = new SaaResponse(saaStatusRequest.getSessionDataKey(), Constants.STATUS_SUCCESS);
            response = Response.status(Response.Status.ACCEPTED)
                    .entity(new Gson().toJson(saaResponse)).build();
        } catch (SQLException e) {

            saaResponse = new SaaResponse(saaStatusRequest.getSessionDataKey(), Constants.STATUS_FAILED);
            response = Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new Gson().toJson(saaResponse)).build();
            log.error("Error occurred while updating status for saa", e);
        }
        log.info("Updated saa status: " + saaResponse);
        return response;
    }

    @POST
    @Path("/serverinitiated/login/ussd/")
    @Consumes("application/json")
    @Produces("application/json")
    public void serverinitiatedLoginUssd(String jsonBody) throws SQLException, JSONException, IOException,
            CommonAuthenticatorException, ConfigurationException {
        Gson gson = new GsonBuilder().serializeNulls().create();
        org.json.JSONObject jsonObj = new org.json.JSONObject(jsonBody);
        String message = jsonObj.getJSONObject("inboundUSSDMessageRequest").getString("inboundUSSDMessage");
        String sessionID = jsonObj.getJSONObject("inboundUSSDMessageRequest").getString("clientCorrelator");
        String spTokenEndpoint = "";
        String spBearerToken = "";
        String responseString;
        Response.Status responseStatus;
        BackChannelTokenResponse backChannelTokenResponse = new BackChannelTokenResponse();
        String status;
        String ussdSessionID = null;
        BackChannelRequestDetails backChannelRequestDetails = null;
        String originalSessionId = sessionID;
        log.debug("Requested session Id: " + originalSessionId);


        AuthenticationContext authenticationContext = getAuthenticationContext(sessionID);
        setStateFromAuthenticationContext(authenticationContext);

        log.info("Received login request");

        if (log.isDebugEnabled()) {
            log.debug("Json Body : " + jsonBody);
        }

        if (sessionID != null) {
            backChannelRequestDetails = DataBaseConnectUtils.getBackChannelUserDetails(sessionID);
        }

        if (jsonObj.getJSONObject("inboundUSSDMessageRequest").has("sessionID") && !jsonObj.getJSONObject
                ("inboundUSSDMessageRequest").isNull("sessionID")) {
            ussdSessionID = jsonObj.getJSONObject("inboundUSSDMessageRequest").getString("sessionID");
            if (log.isDebugEnabled()) {
                log.debug("UssdSessionID 01 : " + ussdSessionID);
            }
        }

        ussdSessionID = ((ussdSessionID != null) ? ussdSessionID : "");
        if (log.isDebugEnabled()) {
            log.debug("UssdSessionID 02 : " + ussdSessionID);
        }

        //Accept or Reject response depending on configured values
        String acceptInputs = configurationService.getDataHolder().getMobileConnectConfig().getUssdConfig()
                .getAcceptUserInputs();
        String rejectInputs = configurationService.getDataHolder().getMobileConnectConfig().getUssdConfig()
                .getRejectUserInputs();

        if (validateUserInputs(acceptInputs, message)) {
            status = "Approved";
            DatabaseUtils.updateStatus(sessionID, status);

            if (authenticationContext != null) {
                DataPublisherUtil.updateAndPublishUserStatus(
                        (UserStatus) authenticationContext.getParameter(Constants.USER_STATUS_DATA_PUBLISHING_PARAM),
                        DataPublisherUtil.UserState.RECEIVE_USSD_PUSH_APPROVED, "USSD login push approved");
            }

            if (backChannelRequestDetails != null) {

                try {
                    String clientSecret = DbUtil.getClientSecret(backChannelRequestDetails.getClientId());

                    List<NameValuePair> tokenRelatedNameValues = new ArrayList<NameValuePair>();
                    tokenRelatedNameValues.add(new BasicNameValuePair("grant_type", "authorization_code"));
                    tokenRelatedNameValues.add(new BasicNameValuePair("code", backChannelRequestDetails.getAuthCode()));
                    tokenRelatedNameValues.add(new BasicNameValuePair("redirect_uri", backChannelRequestDetails
                            .getRedirectUrl()));

                    backChannelTokenResponse = getAccessTokenDetails(backChannelRequestDetails.getCorrelationId(),
                            tokenRelatedNameValues, backChannelRequestDetails.getClientId(), clientSecret);
                    backChannelTokenResponse.setCorrelationId(backChannelRequestDetails.getCorrelationId());
                    backChannelTokenResponse.setAuthReqId(backChannelRequestDetails.getAuthCode());

                } catch (ConfigurationException | AuthenticatorException | CommonAuthenticatorException e) {
                    backChannelTokenResponse = new BackChannelTokenResponse();
                    log.error("Error while generating token for Session Id:" + sessionID);
                    backChannelTokenResponse.setCorrelationId(backChannelRequestDetails.getCorrelationId());
                    backChannelTokenResponse.setAuthReqId(backChannelRequestDetails.getAuthRequestId());
                    backChannelTokenResponse.setError("Internal Server Error");
                    backChannelTokenResponse.setErrorDescription("An error occurred while generating Token Response");
                    /*responseString = Response.status(500).entity(new Gson().toJson(new
                            BackChannelTokenResponse(status, backChannelTokenResponse))).build().toString();*/

                    responseString = new Gson().toJson(new
                            BackChannelTokenResponse(status, backChannelTokenResponse)).toString();
                    responseStatus = Response.Status.INTERNAL_SERVER_ERROR;

                    if (log.isDebugEnabled()) {
                        log.debug("ussdSessionID: " + ussdSessionID + ", Response String: " + responseString + ", Response Status: " + responseStatus);
                    }
                    postTokenRequest(spTokenEndpoint, responseString, spBearerToken);
                }
            }
            responseStatus = Response.Status.OK;
            /*responseString = Response.status(Response.Status.OK).entity(new Gson().toJson(new
                    BackChannelTokenResponse(status, backChannelTokenResponse))).build().toString();*/
        } else if (validateUserInputs(rejectInputs, message)) {
            status = "Rejected";

            if (backChannelRequestDetails != null) {
                backChannelTokenResponse = new BackChannelTokenResponse();
                backChannelTokenResponse.setCorrelationId(backChannelRequestDetails.getCorrelationId());
                backChannelTokenResponse.setAuthReqId(backChannelRequestDetails.getAuthRequestId());
                backChannelTokenResponse.setError(Response.Status.BAD_REQUEST.getReasonPhrase());
                backChannelTokenResponse.setErrorDescription(Response.Status.BAD_REQUEST.getReasonPhrase());
                DatabaseUtils.updateStatus(sessionID, status);
            }

            if (authenticationContext != null) {
                DataPublisherUtil.updateAndPublishUserStatus((UserStatus) authenticationContext.getParameter
                                (Constants.USER_STATUS_DATA_PUBLISHING_PARAM),
                        DataPublisherUtil.UserState.RECEIVE_USSD_PUSH_REJECTED, "USSD login push rejected");
            }
            /*responseString = Response.status(Response.Status.BAD_REQUEST).entity(new Gson().toJson(new
                    BackChannelTokenResponse(status, backChannelTokenResponse))).build().toString();*/
            responseStatus = Response.Status.BAD_REQUEST;
        } else {
            status = "Rejected";

            if (backChannelRequestDetails != null) {
                backChannelTokenResponse = new BackChannelTokenResponse();
                backChannelTokenResponse.setCorrelationId(backChannelRequestDetails.getCorrelationId());
                backChannelTokenResponse.setAuthReqId(backChannelRequestDetails.getAuthRequestId());
                DatabaseUtils.updateStatus(sessionID, status);
                backChannelTokenResponse.setError(Response.Status.NOT_ACCEPTABLE.getReasonPhrase());
                backChannelTokenResponse.setErrorDescription(Response.Status.NOT_ACCEPTABLE.getReasonPhrase());
            }

            if (authenticationContext != null) {
                DataPublisherUtil.updateAndPublishUserStatus((UserStatus) authenticationContext.getParameter
                                (Constants.USER_STATUS_DATA_PUBLISHING_PARAM),
                        DataPublisherUtil.UserState.RECEIVE_USSD_PUSH_FAIL, "USSD login push failed");
            }
            /*responseString = Response.status(Response.Status.NOT_ACCEPTABLE).entity(new Gson().toJson(new
                    BackChannelTokenResponse(status, backChannelTokenResponse))).build().toString();*/
            responseStatus = Response.Status.NOT_ACCEPTABLE;
        }

        if (backChannelRequestDetails != null) {
            spTokenEndpoint = backChannelRequestDetails.getNotificationUrl();
            spBearerToken = backChannelRequestDetails.getNotificationBearerToken();
        } else {
            log.error("Invalid session:" + originalSessionId);
            return;
        }

        responseString = new Gson().toJson(new
                BackChannelTokenResponse(status, backChannelTokenResponse)).toString();

        if (log.isDebugEnabled()) {
            log.debug("ussdSessionID: " + ussdSessionID + ", Response String: " + responseString + ", Response Status: " + responseStatus);
        }

        postTokenRequest(spTokenEndpoint, responseString, spBearerToken);

    }

    //todo: move this to a common util for MIG
    protected void postTokenRequest(String url, String requestStr, String token)
            throws IOException {

        HttpClient client = new DefaultHttpClient();
        HttpPost postRequest = new HttpPost(url);

        postRequest.addHeader("accept", "application/json");
        postRequest.addHeader("Authorization", "Bearer " + token);

        StringEntity input = new StringEntity(requestStr);
        input.setContentType("application/json");

        postRequest.setEntity(input);

        HttpResponse httpResponse = client.execute(postRequest);
        log.info(httpResponse.getStatusLine().getStatusCode());
    }


    @GET
    @Path("/serverinitiated/sms/response/{id}")
    @Produces("text/plain")
    public void serverInitiatedSmsConfirm(@PathParam("id") String sessionID)
            throws SQLException, CommonAuthenticatorException, ConfigurationException, IOException {
        String responseString;
        Response.Status responseStatus;
        AuthenticationContext authenticationContext = getAuthenticationContext(sessionID);
        setStateFromAuthenticationContext(authenticationContext);
        String spTokenEndpoint = "";
        String spBearerToken = "";
        BackChannelTokenResponse backChannelTokenResponse = null;
        String status = null;
        BackChannelRequestDetails backChannelRequestDetails = new BackChannelRequestDetails();

        String originalSessionId = sessionID;
        log.debug("Requested session Id: " + originalSessionId);

        log.info("Processing sms confirmation");
        if (configurationService.getDataHolder().getMobileConnectConfig().getSmsConfig().getIsShortUrl()) {
            // If a URL shortening service is enabled, that means, the id query parameter is the encrypted context
            // identifier. Therefore, to get the actual context identifier, we can decrypt the value of id query param.
            log.debug("A short URL service is enabled in mobile-connect.xml");
            try {
                sessionID = AESencrp.decrypt(sessionID.replaceAll(" ", "+"));
            } catch (Exception e) {
                backChannelTokenResponse = new BackChannelTokenResponse();
                log.error("An error occurred while decrypting session ID", e);
                backChannelTokenResponse.setCorrelationId(backChannelRequestDetails.getCorrelationId());
                backChannelTokenResponse.setAuthReqId(backChannelRequestDetails.getAuthCode());
                backChannelTokenResponse.setError("Internal Server Error");
                backChannelTokenResponse.setErrorDescription("An error occurred while decrypting session ID");
                /*responseString = Response.status(500).entity(new Gson().toJson(new
                        BackChannelTokenResponse(status, backChannelTokenResponse))).build().toString();*/

                responseString = new Gson().toJson(new
                        BackChannelTokenResponse(status, backChannelTokenResponse));
                responseStatus = Response.Status.INTERNAL_SERVER_ERROR;

                if (log.isDebugEnabled()) {
                    log.debug("ussdSessionID: " + sessionID + ", Response String: " + responseString + ", Response Status: " + responseStatus);
                }
                postTokenRequest(spTokenEndpoint, responseString, spBearerToken);
            }
        } else {
            // If a URL shortening service is not enabled, that means, the actual context-identifier was encrypted and
            // a hash key was generated from the encrypted context identifier and a database entry mapping the hash key
            // to the context identifier (not encrypted) should have been inserted.
            // Therefore, to get the context identifier we need to look up the database.
            log.debug("A short URL service is not enabled in mobile-connect.xml");
            try {
                sessionID = DbUtil.getContextIDForHashKey(sessionID);
                if (sessionID == null) {
                    log.debug("There is no context identifier corresponding to the hash id: " + sessionID);
                }
            } catch (AuthenticatorException | SQLException e) {
                backChannelTokenResponse = new BackChannelTokenResponse();
                log.error("An error occurred while retriving context identifier", e);
                backChannelTokenResponse.setCorrelationId(backChannelRequestDetails.getCorrelationId());
                backChannelTokenResponse.setAuthReqId(backChannelRequestDetails.getAuthCode());
                backChannelTokenResponse.setError("Internal Server Error");
                backChannelTokenResponse.setErrorDescription("An error occurred while retriving context identifier");
                /*responseString = Response.status(500).entity(new Gson().toJson(new
                        BackChannelTokenResponse(status, backChannelTokenResponse))).build().toString();*/

                responseString = new Gson().toJson(new
                        BackChannelTokenResponse(status, backChannelTokenResponse));
                responseStatus = Response.Status.INTERNAL_SERVER_ERROR;

                if (log.isDebugEnabled()) {
                    log.debug("ussdSessionID: " + sessionID + ", Response String: " + responseString + ", Response Status: " + responseStatus);
                }
                postTokenRequest(spTokenEndpoint, responseString, spBearerToken);
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Context Identifier: " + sessionID);
        }

        String userStatus = DatabaseUtils.getUSerStatus(sessionID);
        DataPublisherUtil.UserState userState = DataPublisherUtil.UserState.SMS_URL_AUTH_FAIL;

        backChannelRequestDetails = DataBaseConnectUtils.getBackChannelUserDetails(sessionID);

        if (backChannelRequestDetails != null) {
            spTokenEndpoint = backChannelRequestDetails.getNotificationUrl();
            spBearerToken = backChannelRequestDetails.getNotificationBearerToken();
        } else {
            log.error("Invalid session:" + originalSessionId);
            return;
        }

        if (userStatus.equalsIgnoreCase("PENDING")) {
            DatabaseUtils.updateStatus(sessionID, "APPROVED");
            status = "APPROVED";

            if (backChannelRequestDetails != null) {
                try {
                    String clientSecret = DbUtil.getClientSecret(backChannelRequestDetails.getClientId());

                    List<NameValuePair> tokenRelatedNameValues = new ArrayList<NameValuePair>();
                    tokenRelatedNameValues.add(new BasicNameValuePair("grant_type", "authorization_code"));
                    tokenRelatedNameValues.add(new BasicNameValuePair("code", backChannelRequestDetails.getAuthCode()));
                    tokenRelatedNameValues.add(new BasicNameValuePair("redirect_uri", backChannelRequestDetails
                            .getRedirectUrl()));

                    backChannelTokenResponse = getAccessTokenDetails(backChannelRequestDetails.getCorrelationId(),
                            tokenRelatedNameValues, backChannelRequestDetails.getClientId(), clientSecret);
                    backChannelTokenResponse.setCorrelationId(backChannelRequestDetails.getCorrelationId());
                    backChannelTokenResponse.setAuthReqId(backChannelRequestDetails.getAuthCode());

                } catch (ConfigurationException | AuthenticatorException | CommonAuthenticatorException e) {
                    backChannelTokenResponse = new BackChannelTokenResponse();
                    log.error("Error while generating token for Session Id:" + sessionID);
                    backChannelTokenResponse.setCorrelationId(backChannelRequestDetails.getCorrelationId());
                    backChannelTokenResponse.setAuthReqId(backChannelRequestDetails.getAuthCode());
                    backChannelTokenResponse.setError("Internal Server Error");
                    backChannelTokenResponse.setErrorDescription("An error occurred while generating Token Response");

                    /*responseString = Response.status(500).entity(new Gson().toJson(new
                            BackChannelTokenResponse(status, backChannelTokenResponse))).build().toString();*/

                    responseString = new Gson().toJson(new
                            BackChannelTokenResponse(status, backChannelTokenResponse));
                    responseStatus = Response.Status.INTERNAL_SERVER_ERROR;

                    if (log.isDebugEnabled()) {
                        log.debug("ussdSessionID: " + sessionID + ", Response String: " + responseString + ", Response Status: " + responseStatus);
                    }

                    postTokenRequest(spTokenEndpoint, responseString, spBearerToken);
                }


            }
           /* responseString = Response.status(Response.Status.OK).entity(new Gson().toJson(new
                    BackChannelTokenResponse(status, backChannelTokenResponse))).build().toString();*/
            responseStatus = Response.Status.OK;

        } else if (userStatus.equalsIgnoreCase("EXPIRED")) {
            status = "EXPIRED";
            backChannelTokenResponse = new BackChannelTokenResponse();
            backChannelTokenResponse.setCorrelationId(backChannelRequestDetails.getCorrelationId());
            backChannelTokenResponse.setAuthReqId(backChannelRequestDetails.getAuthCode());
            backChannelTokenResponse.setError(Response.Status.BAD_REQUEST.getReasonPhrase());
            backChannelTokenResponse.setErrorDescription(Response.Status.BAD_REQUEST.getReasonPhrase());
            backChannelTokenResponse.setCorrelationId(backChannelRequestDetails.getCorrelationId());

           /* responseString = Response.status(Response.Status.BAD_REQUEST).entity(new Gson().toJson(new
                    BackChannelTokenResponse(status, backChannelTokenResponse))).build().toString();*/
            responseStatus = Response.Status.BAD_REQUEST;
        } else {
            status = "EXPIRED";
            backChannelTokenResponse = new BackChannelTokenResponse();
            backChannelTokenResponse.setCorrelationId(backChannelRequestDetails.getCorrelationId());
            backChannelTokenResponse.setAuthReqId(backChannelRequestDetails.getAuthCode());
            backChannelTokenResponse.setError(Response.Status.BAD_REQUEST.getReasonPhrase());
            backChannelTokenResponse.setErrorDescription(Response.Status.BAD_REQUEST.getReasonPhrase());
            backChannelTokenResponse.setCorrelationId(backChannelRequestDetails.getCorrelationId());

            /*responseString = Response.status(Response.Status.BAD_REQUEST).entity(new Gson().toJson(new
                    BackChannelTokenResponse(status, backChannelTokenResponse))).build().toString();*/
            responseStatus = Response.Status.BAD_REQUEST;
        }


        responseString = new Gson().toJson(new
                BackChannelTokenResponse(status, backChannelTokenResponse));

        if (log.isDebugEnabled()) {
            log.debug("ussdSessionID: " + sessionID + ", Response String: " + responseString + ", Response Status: " + responseStatus);
        }

        postTokenRequest(spTokenEndpoint, responseString, spBearerToken);
    }

    @GET
    @Path("/serverinitiated/smsotp/response/{correlationId}/{smsOtp}")
    @Produces("text/plain")
    public void serverInitiatedSmsOtpConfirm (@PathParam("correlationId") String correlationID, @PathParam("smsOtp") String smsOtp) throws SQLException, CommonAuthenticatorException, ConfigurationException, IOException, AuthenticationFailedException {
        String sessionID = DatabaseUtils.getBackchannelSessionIDForCorrelationID(correlationID);
        if (sessionID == null) {
            log.error("Invalid correlation id:" + correlationID);
            return;
        }
        AuthenticationContext authenticationContext = getAuthenticationContext(sessionID);
        setStateFromAuthenticationContext(authenticationContext);
        BackChannelRequestDetails backChannelRequestDetails = new BackChannelRequestDetails();
        String spTokenEndpoint = "";
        String spBearerToken = "";
        BackChannelTokenResponse backChannelTokenResponse = null;
        String status = null;
        String responseString = null;
        log.info("Received OTP SMS from client Session ID : " +sessionID + " sms otp : " + smsOtp);
        String userStatus = DatabaseUtils.getUSerStatus(sessionID);
        String smsOtpShaValue = generateSHA256Hash(smsOtp);

        backChannelRequestDetails = DataBaseConnectUtils.getBackChannelUserDetails(sessionID);

        if (backChannelRequestDetails != null) {
            spTokenEndpoint = backChannelRequestDetails.getNotificationUrl();
            spBearerToken = backChannelRequestDetails.getNotificationBearerToken();
        } else {
            log.error("Invalid session:" + sessionID);
            return;
        }

        String smsotp = DatabaseUtils.getSMSOTP(sessionID);

        if (smsotp == null || smsotp.equals("")) {
            log.error("sms otp is empty for session id :" + sessionID);
        }


        if (userStatus.equalsIgnoreCase("PENDING") && smsOtpShaValue.equals(smsotp)) {
            DatabaseUtils.updateStatus(sessionID, "APPROVED");
            status = "APPROVED";

            if (backChannelRequestDetails != null) {
                try {
                    String clientSecret = DbUtil.getClientSecret(backChannelRequestDetails.getClientId());

                    List<NameValuePair> tokenRelatedNameValues = new ArrayList<NameValuePair>();
                    tokenRelatedNameValues.add(new BasicNameValuePair("grant_type", "authorization_code"));
                    tokenRelatedNameValues.add(new BasicNameValuePair("code", backChannelRequestDetails.getAuthCode()));
                    tokenRelatedNameValues.add(new BasicNameValuePair("redirect_uri", backChannelRequestDetails
                            .getRedirectUrl()));

                    backChannelTokenResponse = getAccessTokenDetails(backChannelRequestDetails.getCorrelationId(),
                            tokenRelatedNameValues, backChannelRequestDetails.getClientId(), clientSecret);
                    backChannelTokenResponse.setCorrelationId(backChannelRequestDetails.getCorrelationId());
                    backChannelTokenResponse.setAuthReqId(backChannelRequestDetails.getAuthCode());

                } catch (ConfigurationException | AuthenticatorException | CommonAuthenticatorException e) {
                    backChannelTokenResponse = new BackChannelTokenResponse();
                    log.error("Error while generating token for Session Id:" + sessionID);
                    backChannelTokenResponse.setCorrelationId(backChannelRequestDetails.getCorrelationId());
                    backChannelTokenResponse.setAuthReqId(backChannelRequestDetails.getAuthCode());
                    backChannelTokenResponse.setError("Internal Server Error");
                    backChannelTokenResponse.setErrorDescription("An error occurred while generating Token Response");

                    /*responseString = Response.status(500).entity(new Gson().toJson(new
                            BackChannelTokenResponse(status, backChannelTokenResponse))).build().toString();*/

                    responseString = new Gson().toJson(new
                            BackChannelTokenResponse(status, backChannelTokenResponse));

                    if (log.isDebugEnabled()) {
                        log.debug("ussdSessionID: " + sessionID + ", Response String: " + responseString);
                    }

                    postTokenRequest(spTokenEndpoint, responseString, spBearerToken);
                }


            }

        } else if (userStatus.equalsIgnoreCase("EXPIRED")) {
            status = "EXPIRED";
            backChannelTokenResponse = new BackChannelTokenResponse();
            backChannelTokenResponse.setCorrelationId(backChannelRequestDetails.getCorrelationId());
            backChannelTokenResponse.setAuthReqId(backChannelRequestDetails.getAuthCode());
            backChannelTokenResponse.setError(Response.Status.BAD_REQUEST.getReasonPhrase());
            backChannelTokenResponse.setErrorDescription(Response.Status.BAD_REQUEST.getReasonPhrase());
            backChannelTokenResponse.setCorrelationId(backChannelRequestDetails.getCorrelationId());

        } else {
            status = "EXPIRED";
            backChannelTokenResponse = new BackChannelTokenResponse();
            backChannelTokenResponse.setCorrelationId(backChannelRequestDetails.getCorrelationId());
            backChannelTokenResponse.setAuthReqId(backChannelRequestDetails.getAuthCode());
            backChannelTokenResponse.setError(Response.Status.BAD_REQUEST.getReasonPhrase());
            backChannelTokenResponse.setErrorDescription(Response.Status.BAD_REQUEST.getReasonPhrase());
            backChannelTokenResponse.setCorrelationId(backChannelRequestDetails.getCorrelationId());

        }

        responseString = new Gson().toJson(new
                BackChannelTokenResponse(status, backChannelTokenResponse));

        if (log.isDebugEnabled()) {
            log.debug("SessionID: " + sessionID + ", Response String: " + responseString);
        }

        postTokenRequest(spTokenEndpoint, responseString, spBearerToken);

    }

    public BackChannelTokenResponse getAccessTokenDetails(String correlationId, List<NameValuePair>
            tokenRelatedNameValues, String
                                                                  consumerKey, String consumerSecret) throws
            CommonAuthenticatorException, ConfigurationException {
        BackChannelTokenResponse backChannelTokenResponse = null;
        try {
            String tokenCodeEndpoint = mobileConnectConfigs.getBackChannelConfig().getTokenEndpoint();
            HttpClient httpClient = new DefaultHttpClient();
            HttpPost postRequest = new HttpPost(tokenCodeEndpoint);
            String encoding = org.opensaml.xml.util.Base64.encodeBytes((consumerKey + ":" + consumerSecret).getBytes());
            encoding = encoding.substring(0, encoding.length() - 1);

            postRequest.addHeader("Authorization", "Basic " + encoding);
            postRequest.addHeader("Content-Type", "application/x-www-form-urlencoded");
            postRequest.setEntity(new UrlEncodedFormEntity(tokenRelatedNameValues));
            HttpResponse response = httpClient.execute(postRequest);

            BufferedReader br = new BufferedReader(
                    new InputStreamReader((response.getEntity().getContent())));

            String output;
            StringBuilder totalOutput = new StringBuilder();

            while ((output = br.readLine()) != null) {
                totalOutput.append(output);
            }

            log.info("Response to the Access token request:" + totalOutput.toString());
            backChannelTokenResponse = extractValuesFromTokenResponse(totalOutput.toString());

        } catch (IOException ex) {
            log.error("IO Exception occured while getting Access token for correlation ID:" + correlationId + " " +
                    ex.getMessage(), ex);
        } catch (JSONException ex) {
            log.error("JSONException occured while getting Access token for correlation ID:" + correlationId + " " +
                    ex.getMessage(), ex);
        }
        return backChannelTokenResponse;
    }

    private BackChannelTokenResponse extractValuesFromTokenResponse(String tokenResponse) throws
            CommonAuthenticatorException, ConfigurationException {
        JSONObject jObject = new JSONObject(tokenResponse);
        BackChannelTokenResponse backChannelTokenResponse = new BackChannelTokenResponse();
        backChannelTokenResponse.setAccessToken(jObject.getString("access_token"));
        backChannelTokenResponse.setRefreshToken(jObject.getString("refresh_token"));
        backChannelTokenResponse.setIdToken(jObject.getString("id_token"));
        backChannelTokenResponse.setTokenType(jObject.getString("token_type"));
        backChannelTokenResponse.setExpiresIn(jObject.getInt("expires_in"));

        return backChannelTokenResponse;
    }


    /**
     * Ussd receive.
     *
     * @param jsonBody the json body
     * @return the response
     * @throws SQLException  the SQL exception
     * @throws JSONException the JSON exception
     * @throws IOException   Signals that an I/O exception has occurred.
     */
    @POST
    @Path("/login/ussd/")
    @Consumes("application/json")
    @Produces("application/json")
    public Response loginUssd(String jsonBody) throws SQLException, JSONException, IOException {
        Gson gson = new GsonBuilder().serializeNulls().create();
        org.json.JSONObject jsonObj = new org.json.JSONObject(jsonBody);
        String message = jsonObj.getJSONObject("inboundUSSDMessageRequest").getString("inboundUSSDMessage");
        String sessionID = jsonObj.getJSONObject("inboundUSSDMessageRequest").getString("clientCorrelator");
        String msisdn = extractMsisdn(jsonObj);

        AuthenticationContext authenticationContext = getAuthenticationContext(sessionID);
        setStateFromAuthenticationContext(authenticationContext);

        log.info("Received login request");

        if (log.isDebugEnabled()) {
            log.debug("Json Body : " + jsonBody);
        }

        int responseCode = 400;
        String responseString = null;

        String status = null;

        String ussdSessionID = null;
        if (jsonObj.getJSONObject("inboundUSSDMessageRequest").has("sessionID") && !jsonObj.getJSONObject
                ("inboundUSSDMessageRequest").isNull("sessionID")) {
            ussdSessionID = jsonObj.getJSONObject("inboundUSSDMessageRequest").getString("sessionID");
            if (log.isDebugEnabled()) {
                log.debug("UssdSessionID 01 : " + ussdSessionID);
            }
        }
        ussdSessionID = ((ussdSessionID != null) ? ussdSessionID : "");
        if (log.isDebugEnabled()) {
            log.debug("UssdSessionID 02 : " + ussdSessionID);
        }
        //Accept or Reject response depending on configured values
        String acceptInputs = configurationService.getDataHolder().getMobileConnectConfig().getUssdConfig()
                .getAcceptUserInputs();
        String rejectInputs = configurationService.getDataHolder().getMobileConnectConfig().getUssdConfig()
                .getRejectUserInputs();

        if (validateUserInputs(acceptInputs, message)) {
            status = "Approved";
            responseCode = Response.Status.CREATED.getStatusCode();
            DatabaseUtils.updateStatus(sessionID, status);
            if (authenticationContext != null) {
                DataPublisherUtil.updateAndPublishUserStatus(
                        (UserStatus) authenticationContext.getParameter(Constants.USER_STATUS_DATA_PUBLISHING_PARAM),
                        DataPublisherUtil.UserState.RECEIVE_USSD_PUSH_APPROVED, "USSD login push approved");
            }
        } else if (validateUserInputs(rejectInputs, message)) {
            status = "Rejected";
            responseCode = Response.Status.BAD_REQUEST.getStatusCode();
            DatabaseUtils.updateStatus(sessionID, status);
            if (authenticationContext != null) {
                DataPublisherUtil.updateAndPublishUserStatus((UserStatus) authenticationContext.getParameter
                                (Constants.USER_STATUS_DATA_PUBLISHING_PARAM),
                        DataPublisherUtil.UserState.RECEIVE_USSD_PUSH_REJECTED, "USSD login push rejected");
            }
        } else {
            status = "Rejected";
            responseCode = Response.Status.NOT_ACCEPTABLE.getStatusCode();
            DatabaseUtils.updateStatus(sessionID, status);
            if (authenticationContext != null) {
                DataPublisherUtil.updateAndPublishUserStatus((UserStatus) authenticationContext.getParameter
                                (Constants.USER_STATUS_DATA_PUBLISHING_PARAM),
                        DataPublisherUtil.UserState.RECEIVE_USSD_PUSH_FAIL, "USSD login push failed");
            }
        }

        if (responseCode == Response.Status.BAD_REQUEST.getStatusCode() || responseCode == Response.Status
                .NOT_ACCEPTABLE.getStatusCode()) {
            responseString = "{" + "\"requestError\":" + "{"
                    + "\"serviceException\":" + "{" + "\"messageId\":\"" + "SVC0275" + "\"" + "," + "\"text\":\"" +
                    "Internal server Error" + "\"" + "}"
                    + "}}";
        } else {
            responseString = SendUSSD.getUSSDJsonPayload(msisdn, sessionID, 5, "mtfin", ussdSessionID);
        }

        return Response.status(responseCode).entity(responseString).build();
    }

    @POST
    @Path("/registration/ussd")
    @Consumes("application/json")
    @Produces("application/json")
    public Response registrationUssd(String jsonBody) throws SQLException, JSONException, IOException {
        Gson gson = new GsonBuilder().serializeNulls().create();
        org.json.JSONObject jsonObj = new org.json.JSONObject(jsonBody);
        String message = jsonObj.getJSONObject("inboundUSSDMessageRequest").getString("inboundUSSDMessage");
        String sessionID = jsonObj.getJSONObject("inboundUSSDMessageRequest").getString("clientCorrelator");
        String msisdn = extractMsisdn(jsonObj);

        AuthenticationContext authenticationContext = getAuthenticationContext(sessionID);
        setStateFromAuthenticationContext(authenticationContext);

        log.info("Received registration request");

        if (log.isDebugEnabled()) {
            log.debug("Json Body : " + jsonBody);
        }

        int responseCode = 400;
        String responseString = null;

        String status;

        //Accept or Reject response depending on configured values
        String acceptInputs = configurationService.getDataHolder().getMobileConnectConfig().getUssdConfig()
                .getAcceptUserInputs();
        String rejectInputs = configurationService.getDataHolder().getMobileConnectConfig().getUssdConfig()
                .getRejectUserInputs();

        if (validateUserInputs(acceptInputs, message)) {
            if (log.isDebugEnabled()) {
                log.debug("Updating registration status as success");
            }
            status = "Approved";
            responseCode = Response.Status.CREATED.getStatusCode();
            DatabaseUtils.updateRegistrationStatus(sessionID, status);
            if (authenticationContext != null) {
                DataPublisherUtil.updateAndPublishUserStatus((UserStatus) authenticationContext.getParameter
                                (Constants.USER_STATUS_DATA_PUBLISHING_PARAM),
                        DataPublisherUtil.UserState.RECEIVE_USSD_PUSH_APPROVED, "USSD registration push approved");
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Updating registration status as rejected");
            }
            status = "Rejected";
            responseCode = Response.Status.BAD_REQUEST.getStatusCode();
            DatabaseUtils.updateRegistrationStatus(sessionID, status);
            if (authenticationContext != null) {
                DataPublisherUtil.updateAndPublishUserStatus((UserStatus) authenticationContext.getParameter
                                (Constants.USER_STATUS_DATA_PUBLISHING_PARAM),
                        DataPublisherUtil.UserState.RECEIVE_USSD_PUSH_REJECTED, "USSD registration push rejected");
            }
        }

        if (responseCode == Response.Status.BAD_REQUEST.getStatusCode()) {
            responseString = "{" + "\"requestError\":" + "{"
                    + "\"serviceException\":" + "{" + "\"messageId\":\"" + "SVC0275" + "\"" + "," + "\"text\":\"" +
                    "Internal server Error" + "\"" + "}"
                    + "}}";
        }/* else {
            responseString = SendUSSD.getUSSDJsonPayload(msisdn, sessionID, 5, "mtfin",ussdSessionID);
        }*/

        return Response.status(responseCode).entity(responseString).build();
    }

    @POST
    @Path("/pin/login/ussd")
    @Consumes("application/json")
    @Produces("application/json")
    public Response pinLoginUssd(String jsonBody) {
        String response;
        Gson gson = new GsonBuilder().serializeNulls().create();
        org.json.JSONObject jsonObj = new org.json.JSONObject(jsonBody);
        String receivedPin = jsonObj.getJSONObject("inboundUSSDMessageRequest").getString("inboundUSSDMessage");
        String sessionID = jsonObj.getJSONObject("inboundUSSDMessageRequest").getString("clientCorrelator");

        AuthenticationContext authenticationContext = getAuthenticationContext(sessionID);
        setStateFromAuthenticationContext(authenticationContext);

        log.info("Received pin login request");

        PinConfig pinConfig = PinConfigUtil.getPinConfig(authenticationContext);
        pinConfig.setConfirmedPin(getHashedPin(receivedPin));

        boolean isValidFormatPin = isValidPinFormat(receivedPin);
        String msisdn = extractMsisdn(jsonObj);

        String ussdSessionId = getUssdSessionId(jsonObj);

        try {
            if (!isValidFormatPin) {
                response = handleInvalidFormat(gson, sessionID, pinConfig, msisdn, ussdSessionId);
                return Response.status(Response.Status.CREATED).entity(response).build();
            } else if (!pinConfig.isPinsMatched()) {
                response = handlePinMismatchesForLogin(gson, sessionID, pinConfig, msisdn, ussdSessionId);
                return Response.status(Response.Status.CREATED).entity(response).build();
            } else {
                response = getPinMatchedResponse(gson, sessionID, msisdn, ussdSessionId);
                DbUtil.updateRegistrationStatus(sessionID, Constants.STATUS_APPROVED);
                if (authenticationContext != null) {
                    DataPublisherUtil.updateAndPublishUserStatus((UserStatus) authenticationContext.getParameter
                                    (Constants.USER_STATUS_DATA_PUBLISHING_PARAM),
                            DataPublisherUtil.UserState.RECEIVE_USSD_PIN_APPROVED, "USSD pin approved");
                }
                return Response.status(Response.Status.CREATED).entity(response).build();
            }
        } catch (SQLException e) {
            log.error("Error occurred while updating registration status", e);
        } catch (AuthenticatorException e) {
            log.error("Error occurred while inserting to the database", e);
        } catch (AuthenticationFailedException e) {
            log.error("Registered  pin or confirmed is empty");
        }
        USSDRequest ussdRequest = getUssdRequest(msisdn, sessionID, ussdSessionId, Constants.MTFIN, "Error Occurred");
        return Response.status(Response.Status.CREATED).entity(new Gson().toJson(ussdRequest)).build();
    }

    @POST
    @Path("/pin/registration/ussd")
    @Consumes("application/json")
    @Produces("application/json")
    public Response pinRegistrationUssd(String jsonBody) {
        String response;

        Gson gson = new GsonBuilder().serializeNulls().create();
        org.json.JSONObject jsonObj = new org.json.JSONObject(jsonBody);
        String pin = jsonObj.getJSONObject("inboundUSSDMessageRequest").getString("inboundUSSDMessage");
        String sessionID = jsonObj.getJSONObject("inboundUSSDMessageRequest").getString("clientCorrelator");

        AuthenticationContext authenticationContext = getAuthenticationContext(sessionID);
        setStateFromAuthenticationContext(authenticationContext);

        log.info("Received pin registration request");

        PinConfig pinConfig = PinConfigUtil.getPinConfig(authenticationContext);

        boolean isValidFormatPin = isValidPinFormat(pin);
        String msisdn = extractMsisdn(jsonObj);

        String ussdSessionId = getUssdSessionId(jsonObj);

        try {
            if (pinConfig.getCurrentStep() == PinConfig.CurrentStep.REGISTRATION) {

                if (!isValidFormatPin) {
                    response = handleInvalidFormat(gson, sessionID, pinConfig, msisdn, ussdSessionId);
                    return Response.status(Response.Status.CREATED).entity(response).build();
                } else {
                    pinConfig.setCurrentStep(PinConfig.CurrentStep.CONFIRMATION);
                    pinConfig.setRegisteredPin(pin);

                    response = getPinConfirmResponse(gson, pin, sessionID, pinConfig, msisdn, ussdSessionId);
                    return Response.status(Response.Status.CREATED).entity(response).build();
                }
            } else if (pinConfig.getCurrentStep() == PinConfig.CurrentStep.PIN_RESET) {

                pinConfig.setCurrentStep(PinConfig.CurrentStep.CONFIRMATION);
                pinConfig.setRegisteredPin(pin);

                response = getPinConfirmResponse(gson, pin, sessionID, pinConfig, msisdn, ussdSessionId);
                return Response.status(Response.Status.CREATED).entity(response).build();
            } else if (pinConfig.getCurrentStep() == PinConfig.CurrentStep.CONFIRMATION) {
                pinConfig.setConfirmedPin(pin);

                if (!isValidFormatPin) {
                    response = handleInvalidFormat(gson, sessionID, pinConfig, msisdn, ussdSessionId);
                    return Response.status(Response.Status.CREATED).entity(response).build();
                } else if (!pinConfig.isPinsMatched()) {
                    response = handlePinMismatchesForRegistration(gson, sessionID, pinConfig, msisdn, ussdSessionId);
                    return Response.status(Response.Status.CREATED).entity(response).build();
                } else {
                    response = getPinMatchedResponse(gson, sessionID, msisdn, ussdSessionId);

                    DbUtil.updateRegistrationStatus(sessionID, Constants.STATUS_APPROVED);
                    DataPublisherUtil.updateAndPublishUserStatus(
                            (UserStatus) authenticationContext.getParameter(Constants
                                    .USER_STATUS_DATA_PUBLISHING_PARAM),
                            DataPublisherUtil.UserState.RECEIVE_USSD_PIN_APPROVED, "USSD pin approved");

                    pinConfig.setCurrentStep(PinConfig.CurrentStep.PIN_RESET_CONFIRMATION);

                    return Response.status(Response.Status.CREATED).entity(response).build();
                }
            }
        } catch (SQLException e) {
            log.error("Error occurred while updating registration status", e);
        } catch (AuthenticatorException e) {
            log.error("Error occurred while inserting to the database", e);
        } catch (AuthenticationFailedException e) {
            log.error("Registered  pin or confirmed is empty");
        }
        USSDRequest ussdRequest = getUssdRequest(msisdn, sessionID, ussdSessionId, Constants.MTFIN, "" +
                "Invalid Operation");
        return Response.status(Response.Status.CREATED).entity(new Gson().toJson(ussdRequest)).build();
    }

    @GET
    @Path("/validate/answer1/{answer1}/answer2/{answer2}/sessionId/{sessionId}")
    @Produces("application/json")
    public Response validateSecurityQuestions(@PathParam("answer1") String answer1, @PathParam("answer2") String
            answer2,
                                              @PathParam("sessionId") String sessionId) {
        ValidationResponse validationResponse;
        AuthenticationContext authenticationContext = getAuthenticationContext(sessionId);
        setStateFromAuthenticationContext(authenticationContext);

        log.info("Received Q&A validation request");

        PinConfig pinConfig = PinConfigUtil.getPinConfig(authenticationContext);

        String challengeAnswer1 = pinConfig.getChallengeAnswer1();
        String challengeAnswer2 = pinConfig.getChallengeAnswer2();

        if (challengeAnswer1.equalsIgnoreCase(answer1) && challengeAnswer2.equalsIgnoreCase(answer2)) {
            if (log.isDebugEnabled()) {
                log.debug("Q&A are valid");
            }

            String msisdn = (String) authenticationContext.getProperty(Constants.MSISDN);
            String operator = (String) authenticationContext.getProperty(Constants.OPERATOR);
            USSDRequest pinUssdRequest = getPinUssdRequest(msisdn, sessionId);
            try {
                postRequest(getUssdEndpoint(msisdn), new Gson().toJson(pinUssdRequest), operator);
                validationResponse = new ValidationResponse(StatusCode.SUCCESS.getCode(), sessionId, true, true);
            } catch (IOException e) {
                validationResponse = new ValidationResponse(StatusCode.USSD_ERROR.getCode(), sessionId, true, true);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Q&A are invalid. Sending error response");
            }
            validationResponse = new ValidationResponse(StatusCode.VALIDATION_ERROR.getCode(), sessionId,
                    challengeAnswer1.equalsIgnoreCase(answer1), challengeAnswer2.equalsIgnoreCase(answer2));
        }
        return Response.status(Response.Status.OK).entity(new Gson().toJson(validationResponse)).build();

    }

    @POST
    @Path("/save/userChallenges")
    @Consumes("application/json")
    @Produces("application/json")
    public Response saveUserChallenges(String request) {
        AuthenticationDetails authenticationDetails = new Gson().fromJson(request, AuthenticationDetails.class);
        String sessionId = authenticationDetails.getSessionId();
        AuthenticationContext authenticationContext = getAuthenticationContext(sessionId);
        setStateFromAuthenticationContext(authenticationContext);

        log.info("Saving user challenges");

        if (StringUtils.isEmpty(authenticationDetails.getChallengeQuestion1()) ||
                StringUtils.isEmpty(authenticationDetails.getChallengeQuestion2()) ||
                StringUtils.isEmpty(authenticationDetails.getChallengeAnswer1()) ||
                StringUtils.isEmpty(authenticationDetails.getChallengeAnswer2())) {
            throw new IncompleteArgumentException("All inputs must be completed before saving user challenges");
        }

        authenticationContext.setProperty(Constants.CHALLENGE_QUESTION_1, authenticationDetails.getChallengeQuestion1
                ());
        authenticationContext.setProperty(Constants.CHALLENGE_QUESTION_2, authenticationDetails.getChallengeQuestion2
                ());
        authenticationContext.setProperty(Constants.CHALLENGE_ANSWER_1, authenticationDetails.getChallengeAnswer1());
        authenticationContext.setProperty(Constants.CHALLENGE_ANSWER_2, authenticationDetails.getChallengeAnswer2());

        SaveChallengesResponse saveChallengesResponse = new SaveChallengesResponse(sessionId, StatusCode.SUCCESS
                .getCode());
        return Response.status(Response.Status.OK).entity(new Gson().toJson(saveChallengesResponse)).build();

    }

    protected String getUssdEndpoint(String msisdn) {

        MobileConnectConfig.USSDConfig ussdConfig = configurationService.getDataHolder().getMobileConnectConfig()
                .getUssdConfig();

        String url = ussdConfig.getEndpoint();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1) + "/" + "tel:+" + msisdn;

        } else {
            url = url + "/tel:+" + msisdn;
        }

        return url;
    }

    private void setStateFromAuthenticationContext(AuthenticationContext authenticationContext) {
        if (null != authenticationContext) {
            Object state = authenticationContext.getProperty("state");
            Object msisdn = authenticationContext.getProperty(Constants.MSISDN);

            if (null != state) {
                org.apache.log4j.MDC.put("REF_ID", state.toString());
            }

            if (null != msisdn) {
                org.apache.log4j.MDC.put("MSISDN", msisdn.toString());
            }
        }
    }

    private void postRequest(String url, String requestStr, String operator) throws IOException {
        MobileConnectConfig.USSDConfig ussdConfig = configurationService.getDataHolder().getMobileConnectConfig()
                .getUssdConfig();

        final HttpPost postRequest = new HttpPost(url);
        postRequest.addHeader("accept", "application/json");
        postRequest.addHeader("Authorization", "Bearer " + ussdConfig.getAuthToken());

        if (operator != null) {
            postRequest.addHeader("operator", operator);
        }

        StringEntity input = new StringEntity(requestStr);
        input.setContentType("application/json");

        postRequest.setEntity(input);
        final CountDownLatch latch = new CountDownLatch(1);

        if (log.isDebugEnabled()) {
            log.debug("Posting data  [ " + requestStr + " ] to url [ " + url + " ]");
        }
        HttpClient client = new DefaultHttpClient();
        client.execute(postRequest);
    }

    private String getPinMatchedResponse(Gson gson, String sessionID, String msisdn, String ussdSessionId) {
        log.info("Pins are matched");

        USSDRequest ussdRequest;
        String response;
        String message = configurationService.getDataHolder().getMobileConnectConfig().getUssdConfig()
                .getPinRegistrationSuccessMessage();
        ussdRequest = getUssdRequest(msisdn, sessionID, ussdSessionId, Constants.MTFIN, message);
        response = gson.toJson(ussdRequest);

        return response;
    }

    private String getPinConfirmResponse(Gson gson, String pin, String sessionID, PinConfig pinConfig, String msisdn,
                                         String ussdSessionId) {
        log.info("Valid pin received");

        USSDRequest ussdRequest;
        String response;
        String ussdMessage = configurationService.getDataHolder().getMobileConnectConfig().getUssdConfig()
                .getPinConfirmMessage();
        ussdRequest = getUssdRequest(msisdn, sessionID, ussdSessionId, Constants.MTCONT, ussdMessage);

        pinConfig.setCurrentStep(PinConfig.CurrentStep.CONFIRMATION);
        pinConfig.setRegisteredPin(pin);

        response = gson.toJson(ussdRequest);
        return response;
    }

    private String handlePinMismatchesForLogin(Gson gson, String sessionID, PinConfig pinConfig, String msisdn,
                                               String ussdSessionId)
            throws SQLException, AuthenticatorException {
        USSDRequest ussdRequest;
        String response;
        AuthenticationContext authenticationContext = getAuthenticationContext(sessionID);

        if (pinConfig.getPinMismatchAttempts() < Integer.parseInt(configurationService.getDataHolder()
                .getMobileConnectConfig().getUssdConfig().getPinMismatchAttempts()) - 1) {

            String ussdMessage = configurationService.getDataHolder().getMobileConnectConfig().getUssdConfig()
                    .getPinMismatchMessage();
            ussdRequest = getUssdRequest(msisdn, sessionID, ussdSessionId, Constants.MTCONT, ussdMessage);
            response = gson.toJson(ussdRequest);

            if (log.isDebugEnabled()) {
                log.debug("Pin mismatch detected. Sending retry pin message [ " + ussdMessage + " ]");
            }
        } else if (pinConfig.getPinMismatchAttempts() == Integer.parseInt(configurationService.getDataHolder()
                .getMobileConnectConfig().getUssdConfig().getPinMismatchAttempts()) - 1) {

            String ussdMessage = configurationService.getDataHolder().getMobileConnectConfig().getUssdConfig()
                    .getPinResetFlowMessage();
            ussdRequest = getUssdRequest(msisdn, sessionID, ussdSessionId, Constants.MTFIN, ussdMessage);
            response = gson.toJson(ussdRequest);

            DbUtil.updateRegistrationStatus(sessionID, Constants.STATUS_PIN_RESET);
            pinConfig.setCurrentStep(PinConfig.CurrentStep.PIN_RESET);

            if (log.isDebugEnabled()) {
                log.debug("Pin mismatch detected for the last attempt. Terminating ussd session to move user to pin " +
                        "reset flow");
            }
        } else {
            String ussdMessage = configurationService.getDataHolder().getMobileConnectConfig().getUssdConfig()
                    .getPinMismatchAttemptsExceedMessage();
            ussdRequest = getUssdRequest(msisdn, sessionID, ussdSessionId, Constants.MTFIN, ussdMessage);
            response = gson.toJson(ussdRequest);

            DbUtil.updateRegistrationStatus(sessionID, Constants.STATUS_REJECTED);
            if (authenticationContext != null) {
                DataPublisherUtil.updateAndPublishUserStatus((UserStatus) authenticationContext.getParameter
                                (Constants.USER_STATUS_DATA_PUBLISHING_PARAM),
                        DataPublisherUtil.UserState.RECEIVE_USSD_PIN_REJECTED, "USSD pin rejected");
            }
            pinConfig.setCurrentStep(PinConfig.CurrentStep.PIN_RESET);

            if (log.isDebugEnabled()) {
                log.debug("Maximum attempts reached. Sending access denied message [ " + ussdMessage + " ]");
            }
        }
        pinConfig.incrementPinMistmachAttempts();

        return response;
    }

    private String handlePinMismatchesForRegistration(Gson gson, String sessionID, PinConfig pinConfig, String msisdn,
                                                      String ussdSessionId) throws SQLException,
            AuthenticatorException {

        USSDRequest ussdRequest;
        String response;
        AuthenticationContext authenticationContext = getAuthenticationContext(sessionID);

        if (pinConfig.getPinMismatchAttempts() < Integer.parseInt(configurationService.getDataHolder()
                .getMobileConnectConfig().getUssdConfig().getPinMismatchAttempts()) - 1) {

            String ussdMessage = configurationService.getDataHolder().getMobileConnectConfig().getUssdConfig()
                    .getPinMismatchMessage();
            ussdRequest = getUssdRequest(msisdn, sessionID, ussdSessionId, Constants.MTCONT, ussdMessage);
            response = gson.toJson(ussdRequest);

            if (log.isDebugEnabled()) {
                log.debug("Pin mismatch detected. Sending retry pin message [ " + ussdMessage + " ]");
            }
        } else {
            String ussdMessage = configurationService.getDataHolder().getMobileConnectConfig().getUssdConfig()
                    .getPinMismatchAttemptsExceedMessage();
            ussdRequest = getUssdRequest(msisdn, sessionID, ussdSessionId, Constants.MTFIN, ussdMessage);
            response = gson.toJson(ussdRequest);

            DbUtil.updateRegistrationStatus(sessionID, Constants.STATUS_REJECTED);
            if (authenticationContext != null) {
                DataPublisherUtil.updateAndPublishUserStatus((UserStatus) authenticationContext.getParameter
                                (Constants.USER_STATUS_DATA_PUBLISHING_PARAM),
                        DataPublisherUtil.UserState.RECEIVE_USSD_PIN_REJECTED, "USSD pin rejected");
            }

            if (log.isDebugEnabled()) {
                log.debug("Maximum attempts reached. Sending access denied message [ " + ussdMessage + " ]");
            }
        }

        pinConfig.incrementPinMistmachAttempts();

        return response;
    }

    private String handleInvalidFormat(Gson gson, String sessionID, PinConfig pinConfig, String msisdn, String
            ussdSessionId)
            throws SQLException, AuthenticatorException {
        USSDRequest ussdRequest;
        String response;
        AuthenticationContext authenticationContext = getAuthenticationContext(sessionID);

        if (pinConfig.getInvalidFormatAttempts() < Integer.parseInt(configurationService.getDataHolder()
                .getMobileConnectConfig().getUssdConfig().getInvalidFormatPinAttempts())) {
            String message = configurationService.getDataHolder().getMobileConnectConfig().getUssdConfig()
                    .getPinInvalidFormatMessage();
            ussdRequest = getUssdRequest(msisdn, sessionID, ussdSessionId, Constants.MTCONT, message);

            if (log.isDebugEnabled()) {
                log.debug("Invalid pin. Sending retry pin message [ " + message + " ]");
            }
        } else {
            String ussdMessage = configurationService.getDataHolder().getMobileConnectConfig().getUssdConfig()
                    .getPinInvalidFormatAttemptsExceedMessage();
            ussdRequest = getUssdRequest(msisdn, sessionID, ussdSessionId, Constants.MTFIN, ussdMessage);

            DbUtil.updateRegistrationStatus(sessionID, Constants.STATUS_REJECTED);
            if (authenticationContext != null) {
                DataPublisherUtil.updateAndPublishUserStatus((UserStatus) authenticationContext.getParameter
                                (Constants.USER_STATUS_DATA_PUBLISHING_PARAM),
                        DataPublisherUtil.UserState.RECEIVE_USSD_PIN_REJECTED, "USSD pin rejected");
            }
            if (log.isDebugEnabled()) {
                log.debug("Invalid pin maximum attempts reached. Sending access denied message [ " + ussdMessage + " " +
                        "]");
            }
        }
        pinConfig.incrementInvalidFormatAttempts();

        response = gson.toJson(ussdRequest);
        return response;
    }

    private String getUssdSessionId(JSONObject jsonObj) {
        String ussdSessionId = null;

        if (jsonObj.getJSONObject("inboundUSSDMessageRequest").has("sessionID")
                && !jsonObj.getJSONObject("inboundUSSDMessageRequest").isNull("sessionID")) {
            ussdSessionId = jsonObj.getJSONObject("inboundUSSDMessageRequest").getString("sessionID");
            if (log.isDebugEnabled()) {
                log.debug("Ussd session id retrieved [ " + ussdSessionId + " ] ");
            }
        }
        return ussdSessionId;
    }

    private AuthenticationContext getAuthenticationContext(String sessionID) {
        AuthenticationContextCacheKey cacheKey = new AuthenticationContextCacheKey(sessionID);
        AuthenticationContext authenticationContext = null;
        if (cacheKey != null) {
            Object cacheEntryObj = AuthenticationContextCache.getInstance().getValueFromCache(cacheKey);
            if (cacheEntryObj != null) {
                authenticationContext = ((AuthenticationContextCacheEntry) cacheEntryObj).getContext();
            }
        }
        return authenticationContext;
    }

    private boolean isValidPinFormat(String pin) {

        if (pin.matches("[0-9]+") && pin.length() <= Integer.parseInt(configurationService.getDataHolder()
                .getMobileConnectConfig().getUssdConfig().getPinMaxLength())) {
            return true;
        } else {
            return false;
        }
    }

    @GET
    @Path("/registration/ussd/status")
    // @Consumes("application/json")
    @Produces("application/json")
    public Response registrationUserStatus(@QueryParam("sessionId") String sessionId, String jsonBody) throws
            SQLException {

        log.info("Checking user status for session id [ " + sessionId + " ] ");

        String userStatus;
        String responseString;

        AuthenticationContext authenticationContext = getAuthenticationContext(sessionId);
        setStateFromAuthenticationContext(authenticationContext);

        userStatus = DatabaseUtils.getUSerStatus(sessionId);

        setStateFromAuthenticationContext(authenticationContext);
        log.info("UserStatus " + userStatus + " for session id : " + sessionId);

        responseString = "{" + "\"sessionId\":\"" + sessionId + "\"," + "\"status\":\"" + userStatus + "\"" + "}";

        return Response.status(200).entity(responseString).build();
    }

    /**
     * Validate ussd response.
     *
     * @param message       the message
     * @param msisdn        the msisdn
     * @param sessionID     the session id
     * @param ussdSessionID the ussd session id
     * @return the string
     */
    private String validateUSSDResponse(String message, String msisdn, String sessionID, String ussdSessionID) {

        if (log.isDebugEnabled()) {
            log.debug("message : " + message);
            log.debug("msisdn : " + msisdn);
            log.debug("sessionID : " + sessionID);
            log.debug("ussdSessionID : " + ussdSessionID);
        }

        String responseString = null;
        Integer noOfAttempts = ussdNoOfAttempts.get(msisdn);
        if (noOfAttempts == null) {
            ussdNoOfAttempts.put(msisdn, 1);
            noOfAttempts = 0;
        }
        if (noOfAttempts < 2) {//resend USSD request
            responseString = SendUSSD.getUSSDJsonPayload(msisdn, sessionID, noOfAttempts, "mtcont", ussdSessionID);//
            ussdNoOfAttempts.put(msisdn, noOfAttempts + 1);
        }
        return responseString;
    }

    /**
     * Validate pin.
     *
     * @param pin       the pin
     * @param sessionID the session id
     * @param msisdn    the msisdn
     * @return the string
     */
    private String validatePIN(String pin, String sessionID, String msisdn) {

        // load config values
        MobileConnectConfig.SessionUpdaterConfig sessionUpdaterConfig = configurationService.getDataHolder()
                .getMobileConnectConfig().getSessionUpdaterConfig();

        if (log.isDebugEnabled()) {
            log.debug("pin : " + pin);
            log.debug("sessionID : " + sessionID);
            log.debug("msisdn : " + msisdn);
        }

        String responseString = null;
        try {
            LoginAdminServiceClient lAdmin = new LoginAdminServiceClient(sessionUpdaterConfig.getAdmin_url());
            String sessionCookie = lAdmin.authenticate(sessionUpdaterConfig.getAdminusername(), sessionUpdaterConfig
                    .getAdminpassword());
            ClaimManagementClient claimManager = new ClaimManagementClient(sessionUpdaterConfig.getAdmin_url(),
                    sessionCookie);
            String profilePin = claimManager.getPIN(msisdn);
            String hashedUserPin = getHashedPin(pin);
            if (hashedUserPin != null && profilePin != null && profilePin.equals(hashedUserPin)) {
                //success
                return null;
            } else {
                Integer noOfAttempts = DatabaseUtils.readMultiplePasswordNoOfAttempts(sessionID);
                if (noOfAttempts < 2) {//resend USSD
                    responseString = SendUSSD.getJsonPayload(msisdn, sessionID, 2, "mtcont");//send 2 to show
                    // retry_message
                    if (log.isDebugEnabled()) {
                        log.debug("ResponseString 01 : " + responseString);
                    }
                    DatabaseUtils.updateMultiplePasswordNoOfAttempts(sessionID, noOfAttempts + 1);
                } else {//lock user
                    UserIdentityManagementClient identityClient = new UserIdentityManagementClient
                            (sessionUpdaterConfig.getAdmin_url(), sessionCookie);
                    identityClient.lockUser(msisdn);
                    DatabaseUtils.deleteUser(sessionID);
                }
            }
        } catch (AxisFault e) {
            log.error(e);
        } catch (RemoteException e) {
            log.error(e);
        } catch (LoginAuthenticationExceptionException e) {
            log.error(e);
        } catch (SQLException ex) {
            Logger.getLogger(Endpoints.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Endpoints.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UserIdentityManagementAdminServiceIdentityMgtServiceExceptionException ex) {
            Logger.getLogger(Endpoints.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RemoteUserStoreManagerServiceUserStoreExceptionException ex) {
            Logger.getLogger(Endpoints.class.getName()).log(Level.SEVERE, null, ex);
        }
        return responseString;
    }


    /**
     * Validate pin.
     *
     * @param pin           the pin
     * @param sessionID     the session id
     * @param msisdn        the msisdn
     * @param ussdSessionID the ussd session id
     * @return the string
     */
    private String validatePIN(String pin, String sessionID, String msisdn, String ussdSessionID) {

        // load config values
        MobileConnectConfig.SessionUpdaterConfig sessionUpdaterConfig = configurationService.getDataHolder()
                .getMobileConnectConfig().getSessionUpdaterConfig();

        String responseString = null;
        try {
            if (log.isDebugEnabled()) {
                log.debug("Validate PIN");
            }
            LoginAdminServiceClient lAdmin = new LoginAdminServiceClient(sessionUpdaterConfig.getAdmin_url());
            String sessionCookie = lAdmin.authenticate(sessionUpdaterConfig.getAdminusername(), sessionUpdaterConfig
                    .getAdminpassword());
            ClaimManagementClient claimManager = new ClaimManagementClient(sessionUpdaterConfig.getAdmin_url(),
                    sessionCookie);
            String profilePin = claimManager.getPIN(msisdn);

            String hashedUserPin = getHashedPin(pin);


            if (hashedUserPin != null && profilePin != null && profilePin.equals(hashedUserPin)) {
                if (log.isDebugEnabled()) {
                    log.debug("Profile Pin status : success");
                }
                //success
                return null;
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Profile Pin status : fail");
                }
                Integer noOfAttempts = DatabaseUtils.readMultiplePasswordNoOfAttempts(sessionID);
                if (noOfAttempts < 2) {//resend USSD
                    responseString = SendUSSD.getJsonPayload(msisdn, sessionID, 2, "mtcont", ussdSessionID);//send 2
                    // to show retry_message
                    if (log.isDebugEnabled()) {
                        log.info("Retry request : " + responseString);
                    }
                    DatabaseUtils.updateMultiplePasswordNoOfAttempts(sessionID, noOfAttempts + 1);
                } else {//lock user
                    //log.info("####### locked user  : ");
                    //UserIdentityManagementClient identityClient = new UserIdentityManagementClient(FileUtil
                    // .getApplicationProperty("admin_url"), sessionCookie);


                    String failedStatus = "FAILED_ATTEMPTS";
                    if (log.isDebugEnabled()) {
                        log.debug("Updating the database with session:" + sessionID + " and status: " + failedStatus);
                    }
                    DatabaseUtils.updateStatus(sessionID, failedStatus);
                    //DatabaseUtils.updateUSerStatus(sessionID, "FAILED_ATTEMPTS");

                    // identityClient.lockUser(msisdn);
                    DatabaseUtils.deleteUser(sessionID);

                    responseString = SendUSSD.getUSSDJsonPayload(msisdn, sessionID, 9, "mtfin", ussdSessionID);
                }
            }
        } catch (AxisFault e) {
            log.error(e);
        } catch (RemoteException e) {
            log.error(e);
        } catch (LoginAuthenticationExceptionException e) {
            log.error(e);
        } catch (SQLException ex) {
            Logger.getLogger(Endpoints.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Endpoints.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RemoteUserStoreManagerServiceUserStoreExceptionException ex) {
            Logger.getLogger(Endpoints.class.getName()).log(Level.SEVERE, null, ex);
        }
        return responseString;
    }

    /**
     * Ussd pin receive.
     *
     * @param jsonBody the json body
     * @return the response
     * @throws SQLException  the SQL exception
     * @throws JSONException the JSON exception
     */
    @POST
    @Path("/ussd/pin")
    @Consumes("application/json")
    @Produces("application/json")
    public Response ussdPinReceive(String jsonBody) throws SQLException, JSONException {
        Gson gson = new GsonBuilder().serializeNulls().create();
        if (log.isDebugEnabled()) {
            log.info("Request : " + jsonBody);
        }

        org.json.JSONObject jsonObj = new org.json.JSONObject(jsonBody);
        String message = jsonObj.getJSONObject("inboundUSSDMessageRequest").getString("inboundUSSDMessage");
        String sessionID = jsonObj.getJSONObject("inboundUSSDMessageRequest").getString("clientCorrelator");
        String msisdn = extractMsisdn(jsonObj);

        String ussdSessionID = null;

        if (jsonObj.getJSONObject("inboundUSSDMessageRequest").has("sessionID") && !jsonObj.getJSONObject
                ("inboundUSSDMessageRequest").isNull("sessionID")) {

            ussdSessionID = jsonObj.getJSONObject("inboundUSSDMessageRequest").getString("sessionID");

            if (log.isDebugEnabled()) {
                log.debug("UssdSessionID 01 : " + ussdSessionID);
            }
        }

        ussdSessionID = ((ussdSessionID != null) ? ussdSessionID : "");

        if (log.isDebugEnabled()) {
            log.debug("UssdSessionID 02 : " + ussdSessionID);
            log.debug("message : " + message);
            log.debug("sessionID : " + sessionID);
        }

        int responseCode = 400;
//        String responseString = null;
        //validatePIN returns non null value if USSD push should be done again(in case of incorrect PIN)
        String responseString = validatePIN(message, sessionID, msisdn, ussdSessionID);
        if (responseString != null) {
            return Response.status(201).entity(responseString).build();
        }
        String status = null;

        //USSD 1 = YES
        //USSD 2 = NO
        if ((message != null) && (!message.isEmpty())) {
            status = "Approved";
            responseCode = 201;
            DatabaseUtils.updatePinStatus(sessionID, status, message, ussdSessionID);
        } else {
            responseCode = 400;
            status = "Status not updated";
            //nop
        }

        if (responseCode == 400) {
            responseString = "{" + "\"requestError\":" + "{"
                    + "\"serviceException\":" + "{" + "\"messageId\":\"" + "SVC0275" + "\"" + "," + "\"text\":\"" +
                    "Internal server Error" + "\"" + "}"
                    + "}}";
        } else {
            //responseString = "{" + "\"sessionID\":\"" + sessionID + "\","+ "\"status\":\"" + status + "\"" + "}";
            responseString = SendUSSD.getUSSDJsonPayload(msisdn, sessionID, 5, "mtfin", ussdSessionID);
        }
        return Response.status(responseCode).entity(responseString).build();
    }


    /**
     * User status.
     *
     * @param sessionID the session id
     * @param jsonBody  the json body
     * @return the response
     * @throws SQLException the SQL exception
     */
    @GET
    @Path("/ussd/status")
    // @Consumes("application/json")
    @Produces("application/json")
    public Response userStatus(@QueryParam("sessionID") String sessionID, String jsonBody) throws SQLException {

        String responseString = getStatus(sessionID);

        return Response.status(200).entity(responseString).build();
    }

    /**
     * User status for saa.
     *
     * @param sessionID the session id
     * @return the response
     * @throws SQLException the SQL exception
     */
    @GET
    @Path("/saa/status")
    @Produces("application/json")
    public Response getSaaStatus(@QueryParam("sessionId") String sessionID) throws SQLException {

        String responseString = getStatus(sessionID);

        return Response.status(200).entity(responseString).build();
    }

    private String getStatus(@QueryParam("sessionID") String sessionID) throws SQLException {
        String userStatus;
        String responseString;

        AuthenticationContext authenticationContext = getAuthenticationContext(sessionID);
        setStateFromAuthenticationContext(authenticationContext);

        userStatus = DatabaseUtils.getUSerStatus(sessionID);
        log.info("UserStatus " + userStatus + " for session id : " + sessionID);

        responseString = "{" + "\"sessionID\":\"" + sessionID + "\","
                + "\"status\":\"" + userStatus + "\"" + "}";
        return responseString;
    }

    /**
     * Extract msisdn.
     *
     * @param jsonObj the json obj
     * @return the string
     * @throws JSONException the JSON exception
     */
    private String extractMsisdn(JSONObject jsonObj) throws JSONException {
        String address = jsonObj.getJSONObject("inboundUSSDMessageRequest").getString("address");
        if (address != null) {
            return address.split(":\\+")[1];
        }
        return null;
    }

    /**
     * Gets the hashed pin.
     *
     * @param pinvalue the pinvalue
     * @return the hashed pin
     */
    private String getHashedPin(String pinvalue) {
        String hashString = "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pinvalue.getBytes("UTF-8"));

            StringBuilder hexString = new StringBuilder();

            for (int i = 0; i < hash.length; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            hashString = hexString.toString();

        } catch (UnsupportedEncodingException ex) {
            log.error("Error while generating hash value", ex);
        } catch (NoSuchAlgorithmException ex) {
            log.error("Error while generating hash value", ex);
        }

        return hashString;

    }

    /**
     * Login history.
     *
     * @param userID      the user id
     * @param appID       the app id
     * @param strfromDate the strfrom date
     * @param strtoDate   the strto date
     * @return the response
     * @throws SQLException   the SQL exception
     * @throws ParseException the parse exception
     */
    @GET
    @Path("/login/history")
    // @Consumes("application/json")
    @Produces("application/json")
    public Response loginHistory(@QueryParam("userID") String userID, @QueryParam("appID") String appID, @QueryParam
            ("fromDate") String strfromDate,
                                 @QueryParam("toDate") String strtoDate) throws SQLException, ParseException {

        String userStatus = null;
        String responseString = null;
        Date fromDate = new java.sql.Date(new SimpleDateFormat("yyyy-MM-dd").parse(strfromDate).getTime());
        Date toDate = new java.sql.Date(new SimpleDateFormat("yyyy-MM-dd").parse(strtoDate).getTime());
        List<LoginHistory> lsthistory = DatabaseUtils.getLoginHistory(userID, appID, fromDate, toDate);
        responseString = new Gson().toJson(lsthistory);
        return Response.status(200).entity(responseString).build();
    }

    /**
     * Login apps.
     *
     * @param userID the user id
     * @return the response
     * @throws SQLException   the SQL exception
     * @throws ParseException the parse exception
     */
    @GET
    @Path("/login/applications")
    // @Consumes("application/json")
    @Produces("application/json")
    public Response loginApps(@QueryParam("userID") String userID) throws SQLException, ParseException {

        List<String> lsthistory = DatabaseUtils.getLoginApps(userID);
        String responseString = new Gson().toJson(lsthistory);
        return Response.status(200).entity(responseString).build();
    }

    /**
     * Sms confirm.
     *
     * @param sessionID the session id
     * @return the response
     * @throws SQLException the SQL exception
     */
    @GET
    @Path("/sms/response")
    // @Consumes("application/json")
    @Produces("text/plain")
    public Response smsConfirm(@QueryParam("id") String sessionID)
            throws SQLException {
        String responseString;

        AuthenticationContext authenticationContext = getAuthenticationContext(sessionID);
        setStateFromAuthenticationContext(authenticationContext);

        log.info("Processing sms confirmation");
        if (configurationService.getDataHolder().getMobileConnectConfig().getSmsConfig().getIsShortUrl()) {
            // If a URL shortening service is enabled, that means, the id query parameter is the encrypted context
            // identifier. Therefore, to get the actual context identifier, we can decrypt the value of id query param.
            log.debug("A short URL service is enabled in mobile-connect.xml");
            try {
                sessionID = AESencrp.decrypt(sessionID.replaceAll(" ", "+"));
            } catch (Exception e) {
                log.error("An error occurred while decrypting session ID", e);
                responseString = e.getLocalizedMessage();
                return Response.status(500).entity(responseString).build();
            }
        } else {
            // If a URL shortening service is not enabled, that means, the actual context-identifier was encrypted and
            // a hash key was generated from the encrypted context identifier and a database entry mapping the hash key
            // to the context identifier (not encrypted) should have been inserted.
            // Therefore, to get the context identifier we need to look up the database.
            log.debug("A short URL service is not enabled in mobile-connect.xml");
            try {
                sessionID = DbUtil.getContextIDForHashKey(sessionID);
                if (sessionID == null) {
                    log.debug("There is no context identifier corresponding to the hash id: " + sessionID);
                }
            } catch (AuthenticatorException | SQLException e) {
                log.error("An error occurred while retriving context identifier", e);
                return Response.status(500).entity("").build();
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Context Identifier: " + sessionID);
        }
        String status;
        String userStatus = DatabaseUtils.getUSerStatus(sessionID);
        DataPublisherUtil.UserState userState = DataPublisherUtil.UserState.SMS_URL_AUTH_FAIL;
        if (userStatus.equalsIgnoreCase("PENDING")) {
            DatabaseUtils.updateStatus(sessionID, "APPROVED");
            status = "APPROVED";
            responseString = " You are successfully authenticated via mobile-connect";
            userState = DataPublisherUtil.UserState.SMS_URL_AUTH_SUCCESS;
        } else if (userStatus.equalsIgnoreCase("EXPIRED")) {
            status = "EXPIRED";
            responseString = " Your token is expired";
        } else {
            status = "EXPIRED";
            responseString = " Your token has already approved";
        }

        responseString = "{" + "\"status\":\"" + status + "\","
                + "\"text\":\"" + responseString + "\"" + "}";

        log.info("Sending sms confirmation response" + responseString);
        if (authenticationContext != null) {
            DataPublisherUtil.updateAndPublishUserStatus((UserStatus) authenticationContext.getParameter(Constants
                            .USER_STATUS_DATA_PUBLISHING_PARAM),
                    userState, "SMS URL " + status);
        }
        return Response.status(200).entity(responseString).build();
    }

    /**
     * Mepin confirm.
     *
     * @param identifier        the identifier
     * @param transactionId     the transaction id
     * @param allow             the allow
     * @param transactionStatus the transaction status
     * @return the response
     * @throws SQLException the SQL exception
     */
    @POST
    @Path("/mepin/response")
    @Consumes("application/x-www-form-urlencoded")
    public Response mepinConfirm(@FormParam("identifier") String identifier, @FormParam("transaction_id") String
            transactionId, @FormParam("allow") String allow, @FormParam("transaction_status") String
                                         transactionStatus) throws SQLException {

        if (log.isDebugEnabled()) {
            log.debug("MePIN transactionID : " + transactionId);
            log.debug("MePIN identifier : " + identifier);
            log.debug("MePIN transactionStatus : " + transactionStatus);
        }

        MePinStatusRequest mePinStatus = new MePinStatusRequest(transactionId);
        FutureTask<String> futureTask = new FutureTask<String>(mePinStatus);
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.execute(futureTask);

        return Response.status(200).build();
    }

    /**
     * Return the authenticator, based on the defined order in the LOA.xml and
     * the given the acr value.
     *
     * @param acr value of the acr.
     * @return Json string with authenticator.
     * @throws Exception
     */
    @GET
    @Path("/loa/authenticator")
    @Produces("application/json")
    public Response getCorrectAuthenticator(@QueryParam("acr") String acr) throws Exception {

        String returnJson = null;
        int statusCode = 500;
        try {
            log.info("Searching default Authenticator for acr: " + acr);
            Map<String, MIFEAuthentication> authenticationMap = configurationService.getDataHolder()
                    .getAuthenticationLevelMap();
            MIFEAuthentication mifeAuthentication = authenticationMap.get(acr);
            List<MIFEAuthentication.MIFEAbstractAuthenticator> authenticatorList = mifeAuthentication
                    .getAuthenticatorList();

            String selected = selectDefaultAuthenticator(authenticatorList);

            if (selected == null) {
                returnJson = "{\"status\":\"error\", \"message\":\"Invalid configuration in LOA.xml, couldn't find " +
                        "valid Authenticator\"}";
                log.warn("Error: " + returnJson);
                log.info("Response: \n" + returnJson);
            } else {
                returnJson = "{" + "\"acr\":\"" + acr + "\", \"" + "authenticator\":{\"" + "name\":\"" + selected
                        + "\"}" + "}";
                log.info("Default authenticator for acr:" + acr + " is \n" + returnJson);
                log.info("Response: \n" + returnJson);
                statusCode = 200;
            }
        } catch (Exception e) {
            log.error("Error occurred:" + e);
            returnJson = "{\"status\":\"error\", \"message\":\"" + e + "\"}";
            // TODO handle exception.
            throw e;
        }
        return Response.status(statusCode).entity(returnJson).build();
    }

    /**
     * Select the first authenticator from, SMSAuthenticator, USSDAuthenticator
     * or USSDPinAuthenticator
     *
     * @param authenticatorList authenticator list.
     * @return authenticatorName if valid authenticator found.
     */
    private String selectDefaultAuthenticator(List<MIFEAuthentication.MIFEAbstractAuthenticator> authenticatorList) {
        try {
            for (MIFEAuthentication.MIFEAbstractAuthenticator mifeAbstractAuthenticator : authenticatorList) {
                String authenticatorName = mifeAbstractAuthenticator.getAuthenticator();
                if (Constants.smsAuthenticator.equalsIgnoreCase(authenticatorName)
                        || Constants.smsotpAuthenticator.equalsIgnoreCase(authenticatorName)
                        || Constants.ussdAuthenticator.equalsIgnoreCase(authenticatorName)
                        || Constants.ussdPinAuthenticator.equalsIgnoreCase(authenticatorName)
                        || Constants.serverInitiatedUssdPinAuthenticator.equalsIgnoreCase(authenticatorName)) {
                    String msg = "Found valid authenticator: " + authenticatorName;
                    log.debug(msg);
                    log.info(msg);
                    return authenticatorName;
                }
            }
        } catch (Exception e) {
            log.error(e);
        }
        return null;
    }

    private static USSDRequest getUssdContinueRequest(String msisdn, String sessionID, String ussdSessionID, String
            action) {

        USSDRequest req = new USSDRequest();

        OutboundUSSDMessageRequest outboundUSSDMessageRequest = new OutboundUSSDMessageRequest();
        outboundUSSDMessageRequest.setAddress("tel:+" + msisdn);
        outboundUSSDMessageRequest.setShortCode(configurationService.getDataHolder().getMobileConnectConfig()
                .getUssdConfig().getShortCode());
        outboundUSSDMessageRequest.setKeyword(configurationService.getDataHolder().getMobileConnectConfig()
                .getUssdConfig().getKeyword());
        outboundUSSDMessageRequest.setSessionID(ussdSessionID);

        outboundUSSDMessageRequest.setOutboundUSSDMessage(configurationService.getDataHolder().getMobileConnectConfig
                ().getUssdConfig().getPinConfirmMessage());

        outboundUSSDMessageRequest.setClientCorrelator(sessionID);

        ResponseRequest responseRequest = new ResponseRequest();

        responseRequest.setNotifyURL(configurationService.getDataHolder().getMobileConnectConfig().getUssdConfig()
                .getPinRegistrationNotifyUrl());
        responseRequest.setCallbackData("");

        outboundUSSDMessageRequest.setResponseRequest(responseRequest);


        outboundUSSDMessageRequest.setUssdAction(action);
        req.setOutboundUSSDMessageRequest(outboundUSSDMessageRequest);
        return req;
    }

    private static USSDRequest getUssdRequest(String msisdn, String sessionID, String ussdSessionID,
                                              String action, String message) {

        USSDRequest req = new USSDRequest();

        OutboundUSSDMessageRequest outboundUSSDMessageRequest = new OutboundUSSDMessageRequest();
        outboundUSSDMessageRequest.setAddress("tel:+" + msisdn);
        outboundUSSDMessageRequest.setShortCode(configurationService.getDataHolder().getMobileConnectConfig()
                .getUssdConfig().getShortCode());
        outboundUSSDMessageRequest.setKeyword(configurationService.getDataHolder().getMobileConnectConfig()
                .getUssdConfig().getKeyword());
        outboundUSSDMessageRequest.setSessionID(ussdSessionID);

        outboundUSSDMessageRequest.setOutboundUSSDMessage(message);

        outboundUSSDMessageRequest.setClientCorrelator(sessionID);

        ResponseRequest responseRequest = new ResponseRequest();

        responseRequest.setNotifyURL(configurationService.getDataHolder().getMobileConnectConfig().getUssdConfig()
                .getRegistrationNotifyUrl());
        responseRequest.setCallbackData("");

        outboundUSSDMessageRequest.setResponseRequest(responseRequest);


        outboundUSSDMessageRequest.setUssdAction(action);
        req.setOutboundUSSDMessageRequest(outboundUSSDMessageRequest);
        return req;
    }

    private static USSDRequest getUssdInvalidFormatRequest(String msisdn, String sessionID, String ussdSessionID,
                                                           String action) {

        USSDRequest req = new USSDRequest();

        OutboundUSSDMessageRequest outboundUSSDMessageRequest = new OutboundUSSDMessageRequest();
        outboundUSSDMessageRequest.setAddress("tel:+" + msisdn);
        outboundUSSDMessageRequest.setShortCode(configurationService.getDataHolder().getMobileConnectConfig()
                .getUssdConfig().getShortCode());
        outboundUSSDMessageRequest.setKeyword(configurationService.getDataHolder().getMobileConnectConfig()
                .getUssdConfig().getKeyword());
        outboundUSSDMessageRequest.setSessionID(ussdSessionID);

        outboundUSSDMessageRequest.setOutboundUSSDMessage(configurationService.getDataHolder().getMobileConnectConfig
                ().getUssdConfig().getPinInvalidFormatMessage());

        outboundUSSDMessageRequest.setClientCorrelator(sessionID);

        ResponseRequest responseRequest = new ResponseRequest();

        responseRequest.setNotifyURL(configurationService.getDataHolder().getMobileConnectConfig().getUssdConfig()
                .getPinRegistrationNotifyUrl());
        responseRequest.setCallbackData("");

        outboundUSSDMessageRequest.setResponseRequest(responseRequest);


        outboundUSSDMessageRequest.setUssdAction(action);
        req.setOutboundUSSDMessageRequest(outboundUSSDMessageRequest);
        return req;
    }

    protected USSDRequest getPinUssdRequest(String msisdn, String sessionID) {
        MobileConnectConfig.USSDConfig ussdConfig = configurationService.getDataHolder().getMobileConnectConfig()
                .getUssdConfig();

        USSDRequest req = new USSDRequest();

        OutboundUSSDMessageRequest outboundUSSDMessageRequest = new OutboundUSSDMessageRequest();
        outboundUSSDMessageRequest.setAddress("tel:+" + msisdn);
        outboundUSSDMessageRequest.setShortCode(ussdConfig.getShortCode());
        outboundUSSDMessageRequest.setKeyword(ussdConfig.getKeyword());
        outboundUSSDMessageRequest.setOutboundUSSDMessage(ussdConfig.getPinRegistrationMessage());
        outboundUSSDMessageRequest.setClientCorrelator(sessionID);

        ResponseRequest responseRequest = new ResponseRequest();

        responseRequest.setNotifyURL(ussdConfig.getPinRegistrationNotifyUrl());
        responseRequest.setCallbackData("");

        outboundUSSDMessageRequest.setResponseRequest(responseRequest);


        outboundUSSDMessageRequest.setUssdAction(Constants.MTINIT);

        req.setOutboundUSSDMessageRequest(outboundUSSDMessageRequest);
        return req;
    }

    /**
     * Validates ussd user response against a comma separated values string and returns
     * true if list contains the ussd input value.
     *
     * @param ussdInputs comma separated list of possible responses
     * @param ussdValue  value to check
     * @return true if list contains the value to check
     */
    private boolean validateUserInputs(String ussdInputs, String ussdValue) {
        boolean validUserInput = false;

        if (ussdInputs != null && ussdValue != null) {
            String[] validInputsList = ussdInputs.split(",");
            for (String validInput : validInputsList) {
                if (validInput.trim().equalsIgnoreCase(ussdValue)) {
                    validUserInput = true;
                    break;
                }
            }
        } else if (ussdValue != null && ussdValue.equalsIgnoreCase("1")) {
            return true;
        }

        return validUserInput;
    }

    /**
     * Return the status, based on otp validation for user input
     *
     * @param jsonBody value of the request input.
     * @return Json string with status.
     */
    @POST
    @Path("smsotp/send")
    @Consumes("application/json")
    @Produces("application/json")
    public Response validateSMSOTP(
            String jsonBody) {
        String response = null;
        int statusCode = Response.Status.BAD_REQUEST.getStatusCode();
        log.info("Received OTP SMS from client " + jsonBody);
        org.json.JSONObject jsonObj = new org.json.JSONObject(jsonBody);
        String session_id = jsonObj.getString("session_id");
        AuthenticationContext authenticationContext = getAuthenticationContext(session_id);
        setStateFromAuthenticationContext(authenticationContext);
        log.info("Received OTP SMS from client " + jsonBody);
        String otp = jsonObj.getString("otp");
        try {
            DataPublisherUtil.UserState userState = null;
            String state = "failed";
            String smsotp = DatabaseUtils.getSMSOTP(session_id);
            if (smsotp != null && smsotp.equalsIgnoreCase(otp)) {
                DatabaseUtils.updateStatus(session_id, "Approved");
                statusCode = Response.Status.OK.getStatusCode();
                userState = DataPublisherUtil.UserState.SMS_OTP_AUTH_SUCCESS;
                state = "success";
            } else {
                DatabaseUtils.updateStatus(session_id, "Rejected");
                statusCode = Response.Status.FORBIDDEN.getStatusCode();
                userState = DataPublisherUtil.UserState.SMS_OTP_AUTH_FAIL;
            }
            if (authenticationContext != null) {
                DataPublisherUtil.updateAndPublishUserStatus((UserStatus) authenticationContext.getParameter
                        (Constants.USER_STATUS_DATA_PUBLISHING_PARAM), userState, "SMS OTP " + state);
            }
        } catch (SQLException e) {
            log.error("Error occurred while updating sms otp status", e);
        }
        return Response.status(statusCode).entity(response).build();
    }

    public static String generateSHA256Hash(String input) throws AuthenticationFailedException {
        String returnValue=null;
        try{
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(input.getBytes());
            byte byteData[] = md.digest();
            //convert the byte to hex format
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < byteData.length; i++) {
                sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
            }
            returnValue=sb.toString();
        }catch (Exception e){
            throw new AuthenticationFailedException("Failure while hashing the input value",e);
        }
        return returnValue;
    }
}
