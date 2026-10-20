package com.campuscanteen;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.campuscanteen.model.Role;
import com.campuscanteen.security.CanteenUserDetails;
import com.campuscanteen.security.CanteenUserDetailsService;
import com.campuscanteen.service.OrderService;

/**
 * If MongoDB stops answering while the website is running, users must be told so plainly,
 * not shown a page that pretends everything worked or a misleading "wrong password" message.
 * (Spring reports an unreachable database as DataAccessResourceFailureException.)
 */
@EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = ".+")
class DatabaseUnavailableTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @MockitoBean OrderService unreachableOrders;
    @MockitoBean CanteenUserDetailsService unreachableLogin;

    @Test
    void pagesShowDatabaseUnavailable() throws Exception {
        when(unreachableOrders.listForCustomer(anyString()))
                .thenThrow(new DataAccessResourceFailureException("Timed out while waiting for a server"));
        CanteenUserDetails student = new CanteenUserDetails(customer(Role.STUDENT, "21CS900"));
        mvc.perform(get("/home").with(user(student)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(Matchers.containsString("Database unavailable")));
    }

    @Test
    void loginSaysDatabaseUnavailableInsteadOfWrongPassword() throws Exception {
        when(unreachableLogin.loadUserByUsername(any()))
                .thenThrow(new DataAccessResourceFailureException("Timed out while waiting for a server"));
        mvc.perform(formLogin("/login").userParameter("collegeId").user("21CS901").password("password123"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?unavailable"));
        mvc.perform(get("/login").param("unavailable", ""))
                .andExpect(content().string(Matchers.containsString("database cannot be reached")));
    }
}
