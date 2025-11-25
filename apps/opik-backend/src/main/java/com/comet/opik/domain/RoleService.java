package com.comet.opik.domain;

import com.comet.opik.api.Role;
import com.comet.opik.api.RoleScope;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotFoundException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RoleService {

    private final @NonNull RoleDAO roleDAO;

    /**
     * Get role by ID
     *
     * @param roleId the role ID
     * @return the role
     */
    public Optional<Role> getRole(String roleId) {
        return roleDAO.findById(roleId);
    }

    /**
     * Get role by name and scope
     *
     * @param name the role name
     * @param scope the role scope
     * @return the role
     */
    public Optional<Role> getRoleByNameAndScope(String name, RoleScope scope) {
        return roleDAO.findByNameAndScope(name, scope);
    }

    /**
     * Get all builtin roles
     *
     * @return list of builtin roles
     */
    public List<Role> getBuiltinRoles() {
        return roleDAO.findBuiltinRoles();
    }

    /**
     * Get roles by scope
     *
     * @param scope the role scope
     * @return list of roles
     */
    public List<Role> getRolesByScope(RoleScope scope) {
        return roleDAO.findByScope(scope);
    }

    /**
     * Get builtin role by name (convenience method)
     *
     * @param name the role name
     * @param scope the role scope
     * @return the role
     * @throws NotFoundException if role not found
     */
    public Role getBuiltinRole(String name, RoleScope scope) {
        return getRoleByNameAndScope(name, scope)
                .orElseThrow(() -> new NotFoundException(
                        "Builtin role not found: name='%s', scope='%s'".formatted(name, scope)));
    }
}
