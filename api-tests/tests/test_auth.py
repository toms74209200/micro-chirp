import re

import requests

from lib.api_config import BASE_URL
from lib.tracing import Phases

UUID_PATTERN = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")


def test_post_auth_login_normal(phases: Phases):
    phases.act()
    response = requests.post(f"{BASE_URL}/auth/login")

    phases.assert_()
    assert response.status_code == 200
    user_id = response.json()["userId"]
    assert UUID_PATTERN.match(user_id)
