package com.mindplates.nextchapter.application.admin.port.out;

import com.mindplates.nextchapter.application.admin.view.AdminTokenView;
import com.mindplates.nextchapter.domain.admin.model.AdminAccount;

/** JWT 발급. 서명 방식·클레임 구성은 security 어댑터가 안다. */
public interface AdminTokenIssuerPort {

    AdminTokenView issue(AdminAccount account);
}
