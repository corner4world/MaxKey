/*
 * Copyright [2020] [MaxKey of copyright http://www.maxkey.top]
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 

package org.maxkey.web.endpoint;

import java.io.IOException;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.maxkey.authn.AbstractAuthenticationProvider;
import org.maxkey.authn.LoginCredential;
import org.maxkey.authn.support.kerberos.KerberosService;
import org.maxkey.authn.support.onetimepwd.AbstractOtpAuthn;
import org.maxkey.authn.support.rememberme.AbstractRemeberMeService;
import org.maxkey.authn.support.socialsignon.service.SocialSignOnProviderService;
import org.maxkey.authn.support.wsfederation.WsFederationConstants;
import org.maxkey.configuration.ApplicationConfig;
import org.maxkey.constants.ConstantsStatus;
import org.maxkey.domain.UserInfo;
import org.maxkey.persistence.service.UserInfoService;
import org.maxkey.util.StringUtils;
import org.maxkey.web.WebConstants;
import org.maxkey.web.WebContext;
import org.maxkey.web.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;


/**
 * @author Crystal.Sea
 *
 */
@Controller
public class LoginEndpoint {
	private static Logger _logger = LoggerFactory.getLogger(LoginEndpoint.class);
	
	
	
	@Autowired
  	@Qualifier("applicationConfig")
  	ApplicationConfig applicationConfig;
 	
	@Autowired
	@Qualifier("socialSignOnProviderService")
	SocialSignOnProviderService socialSignOnProviderService;
	
	@Autowired
	@Qualifier("remeberMeService")
	AbstractRemeberMeService remeberMeService;
	
	@Autowired
	@Qualifier("kerberosService")
	KerberosService kerberosService;
	
	@Autowired
	@Qualifier("userInfoService")
	UserInfoService userInfoService;
	
	/*@Autowired
	@Qualifier("wsFederationService")
	WsFederationService wsFederationService;*/
	
	@Autowired
	@Qualifier("authenticationProvider")
	AbstractAuthenticationProvider authenticationProvider ;
	
	@Autowired
    @Qualifier("tfaOptAuthn")
    protected AbstractOtpAuthn tfaOptAuthn;
	
	/*
	@Autowired
	@Qualifier("jwtLoginService")
	JwtLoginService jwtLoginService;
	*/
	/**
	 * init login
	 * @return
	 */
 	@RequestMapping(value={"/login"})
	public ModelAndView login(
			HttpServletRequest request,
			HttpServletResponse response,
			@CookieValue(value=WebConstants.REMEBER_ME_COOKIE,required=false) String remeberMe,
			@RequestParam(value=WebConstants.CAS_SERVICE_PARAMETER,required=false) String casService,
			@RequestParam(value=WebConstants.KERBEROS_TOKEN_PARAMETER,required=false) String kerberosToken,
			@RequestParam(value=WebConstants.KERBEROS_USERDOMAIN_PARAMETER,required=false) String kerberosUserDomain,
			@RequestParam(value=WsFederationConstants.WA,required=false) String wsFederationWA,
			@RequestParam(value=WsFederationConstants.WRESULT,required=false) String wsFederationWResult) {
 		
		_logger.debug("LoginController /login.");
		ModelAndView modelAndView = new ModelAndView("login");
		
		boolean isAuthenticated= WebContext.isAuthenticated();
		//for RemeberMe login
		if(!isAuthenticated){
			if(applicationConfig.getLoginConfig().isRemeberMe()&&remeberMe!=null&& !remeberMe.equals("")){
				_logger.debug("Try RemeberMe login ");
				isAuthenticated=remeberMeService.login(remeberMe,response);
			}
		}
		//for Kerberos login
		if(!isAuthenticated){
			if(applicationConfig.getLoginConfig().isKerberos()&&
					kerberosUserDomain!=null&&!kerberosUserDomain.equals("")&&
					kerberosToken!=null && !kerberosToken.equals("")){
				_logger.debug("Try Kerberos login ");
				isAuthenticated=kerberosService.login(kerberosToken,kerberosUserDomain);
			}
		}
		//for WsFederation login
		if(!isAuthenticated){
			if(applicationConfig.getLoginConfig().isWsFederation()&&
					StringUtils.isNotEmpty(wsFederationWA) && 
					wsFederationWA.equalsIgnoreCase(WsFederationConstants.WSIGNIN)){
				_logger.debug("Try WsFederation login ");
				//isAuthenticated=wsFederationService.login(wsFederationWA,wsFederationWResult,request);
			}
		}
				
		//for normal login
		if(!isAuthenticated){
			modelAndView.addObject("isRemeberMe", applicationConfig.getLoginConfig().isRemeberMe());
			modelAndView.addObject("isKerberos", applicationConfig.getLoginConfig().isKerberos());
			modelAndView.addObject("isMfa", applicationConfig.getLoginConfig().isMfa());
			if(applicationConfig.getLoginConfig().isMfa()) {
			    modelAndView.addObject("optType", tfaOptAuthn.getOptType());
			    modelAndView.addObject("optInterval", tfaOptAuthn.getInterval());
			}
			
			if( applicationConfig.getLoginConfig().isKerberos()){
				modelAndView.addObject("userDomainUrlJson", kerberosService.buildKerberosProxys());
				
			}
			modelAndView.addObject("isCaptcha", applicationConfig.getLoginConfig().isCaptcha());
			modelAndView.addObject("sessionid", WebContext.getSession().getId());
			//modelAndView.addObject("jwtToken",jwtLoginService.buildLoginJwt());
			//load Social Sign On Providers
			if(applicationConfig.getLoginConfig().isSocialSignOn()){
				_logger.debug("Load Social Sign On Providers ");
				modelAndView.addObject("ssopList", socialSignOnProviderService.getSocialSignOnProviders());
			}
		}
		
		
		if(isAuthenticated){
			return  WebContext.redirect("/forwardindex");
		}
		
		Object loginErrorMessage=WebContext.getAttribute(WebConstants.LOGIN_ERROR_SESSION_MESSAGE);
        modelAndView.addObject("loginErrorMessage", loginErrorMessage==null?"":loginErrorMessage);
        WebContext.removeAttribute(WebConstants.LOGIN_ERROR_SESSION_MESSAGE);
		return modelAndView;
	}
 	
 	@RequestMapping(value={"/logon.do"})
	public ModelAndView logon(
	                    HttpServletRequest request,
	                    HttpServletResponse response,
	                    @ModelAttribute("loginCredential") LoginCredential loginCredential) throws ServletException, IOException {

        authenticationProvider.authenticate(loginCredential);

        if (WebContext.isAuthenticated()) {
            return WebContext.redirect("/forwardindex");
        } else {
            return WebContext.redirect("/login");
        }
 		
 	}
	
 	
 	@RequestMapping("/login/{username}")
	@ResponseBody
	public HashMap <String,Object> queryLoginUserAuth(@PathVariable("username") String username) {
 		UserInfo userInfo=new UserInfo();
 		userInfo.setUsername(username);
 		userInfo=userInfoService.load(userInfo);
 		
 		HashMap <String,Object> authnType=new HashMap <String,Object>();
 		authnType.put("authnType", userInfo.getAuthnType());
 		authnType.put("appLoginAuthnType", userInfo.getAppLoginAuthnType());
 		
 		return authnType;
 	}
 	
 	@RequestMapping("/login/otp/{username}")
    @ResponseBody
    public String produceOtp(@PathVariable("username") String username) {
        UserInfo userInfo = new UserInfo();
        userInfo.setUsername(username);
        UserInfo queryUserInfo=userInfoService.loadByUsername(username);//(userInfo);
        if(queryUserInfo!=null) {
            tfaOptAuthn.produce(queryUserInfo);
            return "ok";
        }
        
        return "fail";
    }
 	
 	/**
	 * view register
	 * @return
	 */
 	@RequestMapping(value={"/register"})
	public ModelAndView register(HttpServletRequest request,HttpServletResponse response) {
 		
		_logger.debug("LoginController /register.");
		ModelAndView modelAndView = new ModelAndView("registration/register");
		Object loginErrorMessage=WebContext.getAttribute(WebConstants.LOGIN_ERROR_SESSION_MESSAGE);
        modelAndView.addObject("loginErrorMessage", loginErrorMessage==null?"":loginErrorMessage);
        WebContext.removeAttribute(WebConstants.LOGIN_ERROR_SESSION_MESSAGE);
		return modelAndView;
	}
 	
 	@RequestMapping(value={"/registeron"})
 	@ResponseBody
	public Message registeron(UserInfo userInfo,@RequestParam String emailMobile) throws ServletException, IOException {
 		if(StringUtils.isNullOrBlank(emailMobile)) {
 			return new Message(WebContext.getI18nValue("register.emailMobile.error"),"1");
 		}
 		if(StringUtils.isValidEmail(emailMobile)) {
 			userInfo.setEmail(emailMobile);
 		}
 		if(StringUtils.isValidMobileNo(emailMobile)) {
 			userInfo.setMobile(emailMobile);
 		}
 		if(!(StringUtils.isValidEmail(emailMobile)||StringUtils.isValidMobileNo(emailMobile))) {
 			return new Message(WebContext.getI18nValue("register.emailMobile.error"),"1");
 		}
 		UserInfo temp=userInfoService.queryUserInfoByEmailMobile(emailMobile);
 		if(temp!=null) {
 			return new Message(WebContext.getI18nValue("register.emailMobile.exist"),"1");
 		}
 		
 		temp=userInfoService.loadByUsername(userInfo.getUsername());
 		if(temp!=null) {
 			return new Message(WebContext.getI18nValue("register.user.error"),"1");
 		}
 		userInfo.setStatus(ConstantsStatus.ACTIVE);
 		if(userInfoService.insert(userInfo)) {
 			return new Message(WebContext.getI18nValue("login.text.register.success"),"0");
 		}
 		return new Message(WebContext.getI18nValue("login.text.register.error"),"1");
 		
 	}
}
