package org.solent.com504.project.impl.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.solent.com504.project.impl.validator.UserValidator;
import org.solent.com504.project.model.party.dto.Address;
import org.solent.com504.project.model.party.dto.Party;
import org.solent.com504.project.model.party.service.PartyService;
import org.solent.com504.project.model.user.dto.Role;
import org.solent.com504.project.model.user.dto.User;
import org.solent.com504.project.model.user.dto.UserRoles;
import org.solent.com504.project.model.user.service.SecurityService;
import org.solent.com504.project.model.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UserController {

    final static Logger LOG = LogManager.getLogger(UserController.class);

    {
        LOG.debug("UserController created");
    }

    @Autowired
    private UserService userService;

    @Autowired
    private PartyService partyService;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private UserValidator userValidator;

    @RequestMapping(value = "/registration", method = RequestMethod.GET)
    public String registration(Model model) {
        model.addAttribute("userForm", new User());

        return "registration";
    }

    @RequestMapping(value = "/registration", method = RequestMethod.POST)
    public String registration(@ModelAttribute("userForm") User userForm, BindingResult bindingResult, Model model) {
        userValidator.validate(userForm, bindingResult);

        if (bindingResult.hasErrors()) {
            return "registration";
        }

        userService.create(userForm);

        // if not logged in then log in as new party
        // if logged in, stay as present party (e.g. global admin)
        if (!hasRole(UserRoles.ROLE_USER.name())) {
            LOG.debug("creating new user and logging in : " + userForm);
            securityService.autologin(userForm.getUsername(), userForm.getPasswordConfirm());
        } else {
            LOG.debug("creating new user : " + userForm);
        }

        return "redirect:/viewModifyUser?username=" + userForm.getUsername();
    }

    @RequestMapping(value = "/denied", method = {RequestMethod.GET, RequestMethod.POST})
    public String denied(Model model) {
        return "denied";
    }

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String login(Model model, String error, String logout) {
        if (error != null) {
            model.addAttribute("error", "Your username and password is invalid.");
        }

        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully.");
        }

        return "login";
    }

    @RequestMapping(value = {"/", "/home"}, method = RequestMethod.GET)
    public String home(Model model) {
        return "home";
    }

    @RequestMapping(value = {"/about"}, method = RequestMethod.GET)
    public String about(Model model) {
        return "about";
    }

    @RequestMapping(value = {"/contact"}, method = RequestMethod.GET)
    public String contact(Model model) {
        return "contact";
    }

    @RequestMapping(value = {"/users"}, method = RequestMethod.GET)
    public String users(Model model) {
        List<User> userList = userService.findAll();

        LOG.debug("users called:");
        for (User user : userList) {
            LOG.debug(" user:" + user);
        }

        model.addAttribute("userListSize", userList.size());
        model.addAttribute("userList", userList);

        return "users";
    }

    @RequestMapping(value = {"/viewModifyUser"}, method = RequestMethod.GET)
    public String modifyuser(Model model,
            @RequestParam(value = "username", required = true) String username, Authentication authentication) {

        // security check if party is allowed to access or modify this party
        if (!hasRole(UserRoles.ROLE_GLOBAL_ADMIN.name())) {
            if (!username.equals(authentication.getName())) {
                LOG.warn("security warning without permissions, modifyuser called for username=" + username);
                return ("denied");
            }
        }

        User user = userService.findByUsername(username);
        if (user == null) {
            LOG.warn("security warning modifyuser called for unknown username=" + username);
            return ("denied");
        }

        LOG.debug("viewUser called for username=" + username + " user=" + user);
        model.addAttribute("user", user);

        Map<String, String> selectedRolesMap = selectedRolesMap(user);

        for (Entry entry : selectedRolesMap.entrySet()) {
            LOG.debug(username + " role:" + entry.getKey() + " selected:" + entry.getValue());
        }

        model.addAttribute("selectedRolesMap", selectedRolesMap);

        return "viewModifyUser";
    }

    @RequestMapping(value = {"/viewModifyUser"}, method = RequestMethod.POST)
    public String updateuser(Model model,
            @RequestParam(value = "username", required = true) String username,
            @RequestParam(value = "firstName", required = false) String firstName,
            @RequestParam(value = "secondName", required = false) String secondName,
            @RequestParam(value = "selectedRoles", required = false) List<String> selectedRolesIn,
            @RequestParam(value = "userEnabled", required = false) String userEnabled,
            @RequestParam(value = "number", required = false) String number,
            @RequestParam(value = "addressLine1", required = false) String addressLine1,
            @RequestParam(value = "addressLine2", required = false) String addressLine2,
            @RequestParam(value = "county", required = false) String county,
            @RequestParam(value = "country", required = false) String country,
            @RequestParam(value = "postcode", required = false) String postcode,
            @RequestParam(value = "latitude", required = false) String latitude,
            @RequestParam(value = "longitude", required = false) String longitude,
            @RequestParam(value = "telephone", required = false) String telephone,
            @RequestParam(value = "mobile", required = false) String mobile,
            Authentication authentication
    ) {
        LOG.debug("updateUser called for username=" + username);

        // security check if party is allowed to access or modify this party
        if (!hasRole(UserRoles.ROLE_GLOBAL_ADMIN.name())) {
            if (!username.equals(authentication.getName())) {
                LOG.warn("security warning without permissions, updateUser called for username=" + username);
                return ("denied");
            }
        }

        User user = userService.findByUsername(username);
        if (user == null) {
            LOG.warn("security warning updateUser called for unknown username=" + username);
            return ("denied");
        }

        String errorMessage = "";

        if (firstName != null) {
            user.setFirstName(firstName);
        }
        if (secondName != null) {
            user.setSecondName(secondName);
        }
        if (userEnabled != null) {
            user.setEnabled(Boolean.TRUE);
        } else {
            user.setEnabled(Boolean.FALSE);
        }

        Address address = new Address();
        address.setNumber(number);
        address.setAddressLine1(addressLine1);
        address.setAddressLine2(addressLine2);
        address.setCountry(country);
        address.setCounty(county);
        address.setPostcode(postcode);
        address.setMobile(mobile);
        address.setTelephone(telephone);
        try {
            address.setLatitude(Double.parseDouble(latitude));
            address.setLongitude(Double.parseDouble(longitude));
        } catch (Exception ex) {
            errorMessage = "problem parsing latitude=" + latitude
                    + " or longitude=" + longitude;
        }
        user.setAddress(address);

        user = userService.save(user);

        // update roles if roles in list
        if (selectedRolesIn != null) {
            user = userService.updateUserRoles(username, selectedRolesIn);
        }

        Map<String, String> selectedRolesMap = selectedRolesMap(user);

        model.addAttribute("user", user);

        model.addAttribute("selectedRolesMap", selectedRolesMap);

        // add message if there are any 
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("message", "User " + user.getUsername() + " updated successfully");

        return "viewModifyUser";
    }

    private Map<String, String> selectedRolesMap(User user) {

        List<String> availableRoles = userService.getAvailableUserRoleNames();

        List<String> selectedRoles = new ArrayList();
        for (Role role : user.getRoles()) {
            selectedRoles.add(role.getName());
            LOG.debug("user " + user.toString()
                    + "roles from database:" + role.getName());
        }

        Map<String, String> selectedRolesMap = new LinkedHashMap();
        for (String availableRole : availableRoles) {
            if (selectedRoles.contains(availableRole)) {
                selectedRolesMap.put(availableRole, "checked");
                LOG.debug("availableRole " + availableRole
                        + " user " + user.toString() + " available role:checked");
            } else {
                selectedRolesMap.put(availableRole, "");
                LOG.debug("availableRole " + availableRole
                        + " user " + user.toString() + " available role:not checked");
            }
        }

        return selectedRolesMap;

    }

    /**
     * returns true if the party has the role specified
     *
     * @param role
     * @return
     */
    private boolean hasRole(String role) {
        Collection<GrantedAuthority> authorities
                = (Collection<GrantedAuthority>) SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        boolean hasRole = false;
        for (GrantedAuthority authority : authorities) {
            hasRole = authority.getAuthority().equals(role);
            if (hasRole) {
                break;
            }
        }
        return hasRole;
    }

    // PARTY MANAGEMENT
    @RequestMapping(value = {"/partys"}, method = RequestMethod.GET)
    @Transactional
    public String partys(Model model) {

        LOG.debug("partys called:");
        List<Party> partyList = partyService.findAll();

        for (Party party : partyList) {
            LOG.debug(" party:" + party + " users.size="
                    + ((party.getUsers() == null) ? "null" : party.getUsers().size()));
        }

        model.addAttribute("partyListSize", partyList.size());
        model.addAttribute("partyList", partyList);

        return "partys";
    }

    @RequestMapping(value = {"/viewModifyParty"}, method = RequestMethod.GET)
    public String reviewParty(Model model,
            @RequestParam(value = "partyuuid", required = true) String uuid, Authentication authentication) {

        LOG.debug("viewModifyParty called for uuid=" + uuid);

        // security check if party is allowed to access or modify this party
//        if (!hasRole(UserRoles.ROLE_GLOBAL_ADMIN.name())) {
//            if (!uuid.equals(authentication.getName())) {
//                LOG.warn("security warning without permissions, modifyuser called for uuid=" + uuid);
//                return ("denied");
//            }
//        }

        Party party = partyService.findByUuid(uuid);
        if (party == null) {
            LOG.warn("security warning modifyparty called for unknown uuid=" + uuid);
            return ("denied");
        }

        LOG.debug("viewUser called for uuid=" + uuid + " party=" + party);
        model.addAttribute("party", party);

        Map<String, String> selectedRolesMap = new HashMap(); // = selectedRolesMap(party);
        //for (Entry entry : selectedRolesMap.entrySet()) {
        //   LOG.debug(uuid + " role:" + entry.getKey() + " selected:" + entry.getValue());
        // }
        model.addAttribute("selectedRolesMap", selectedRolesMap);
        return "viewModifyParty";
    }

    @RequestMapping(value = {"/viewModifyParty"}, method = RequestMethod.POST)
    public String updateParty(Model model,
            @RequestParam(value = "username", required = true) String partyuuid,
            @RequestParam(value = "firstName", required = false) String firstName,
            @RequestParam(value = "secondName", required = false) String secondName,
            @RequestParam(value = "selectedRoles", required = false) List<String> selectedRolesIn,
            @RequestParam(value = "userEnabled", required = false) String userEnabled,
            @RequestParam(value = "number", required = false) String number,
            @RequestParam(value = "addressLine1", required = false) String addressLine1,
            @RequestParam(value = "addressLine2", required = false) String addressLine2,
            @RequestParam(value = "county", required = false) String county,
            @RequestParam(value = "country", required = false) String country,
            @RequestParam(value = "postcode", required = false) String postcode,
            @RequestParam(value = "latitude", required = false) String latitude,
            @RequestParam(value = "longitude", required = false) String longitude,
            @RequestParam(value = "telephone", required = false) String telephone,
            @RequestParam(value = "mobile", required = false) String mobile,
            Authentication authentication
    ) {
        LOG.debug("updateUser called for username=" + partyuuid);

        // security check if party is allowed to access or modify this party
        if (!hasRole(UserRoles.ROLE_GLOBAL_ADMIN.name())) {
            if (!partyuuid.equals(authentication.getName())) {
                LOG.warn("security warning without permissions, updateUser called for username=" + partyuuid);
                return ("denied");
            }
        }

        User user = userService.findByUsername(partyuuid);
        if (user == null) {
            LOG.warn("security warning updateUser called for unknown username=" + partyuuid);
            return ("denied");
        }

        String errorMessage = "";

        if (firstName != null) {
            user.setFirstName(firstName);
        }
        if (secondName != null) {
            user.setSecondName(secondName);
        }
        if (userEnabled != null) {
            user.setEnabled(Boolean.TRUE);
        } else {
            user.setEnabled(Boolean.FALSE);
        }

        Address address = new Address();
        address.setNumber(number);
        address.setAddressLine1(addressLine1);
        address.setAddressLine2(addressLine2);
        address.setCountry(country);
        address.setCounty(county);
        address.setPostcode(postcode);
        address.setMobile(mobile);
        address.setTelephone(telephone);
        try {
            address.setLatitude(Double.parseDouble(latitude));
            address.setLongitude(Double.parseDouble(longitude));
        } catch (Exception ex) {
            errorMessage = "problem parsing latitude=" + latitude
                    + " or longitude=" + longitude;
        }
        user.setAddress(address);

        user = userService.save(user);

        // update roles if roles in list
        if (selectedRolesIn != null) {
            user = userService.updateUserRoles(partyuuid, selectedRolesIn);
        }

        Map<String, String> selectedRolesMap = selectedRolesMap(user);

        model.addAttribute("user", user);

        model.addAttribute("selectedRolesMap", selectedRolesMap);

        // add message if there are any 
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("message", "User " + user.getUsername() + " updated successfully");

        return "viewModifyParty";
    }

}
