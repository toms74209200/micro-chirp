import re
from uuid import uuid4

import requests

from lib.api_config import BASE_URL
from openapi_gen.micro_chirp_api_client.api.auth import post_auth_login
from openapi_gen.micro_chirp_api_client.api.posts import post_posts
from openapi_gen.micro_chirp_api_client.client import Client
from openapi_gen.micro_chirp_api_client.models.post_posts_body import PostPostsBody

UUID_PATTERN = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
ISO8601_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})$")


def test_get_timeline_global_with_valid_request_returns_200():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = str(auth_response.user_id)

    expected_content = f"Test content {uuid4().hex[:8]}"
    body = PostPostsBody(user_id=auth_response.user_id, content=expected_content)
    create_response = post_posts.sync(client=client, body=body)
    assert create_response is not None
    post_id = str(create_response.post_id)

    response = requests.get(f"{BASE_URL}/timeline/global", params={"limit": 1})

    assert response.status_code == 200
    data = response.json()
    assert data["limit"] == 1
    assert data["posts"][0]["postId"] == post_id
    assert data["posts"][0]["userId"] == user_id
    assert data["posts"][0]["content"] == expected_content
    assert UUID_PATTERN.match(data["posts"][0]["postId"])
    assert UUID_PATTERN.match(data["posts"][0]["userId"])
    assert ISO8601_PATTERN.match(data["posts"][0]["createdAt"])
    assert data["posts"][0]["likeCount"] == 0
    assert data["posts"][0]["repostCount"] == 0
    assert data["posts"][0]["replyCount"] == 0
    assert data["posts"][0]["viewCount"] == 0
    assert data["posts"][0]["isLikedByCurrentUser"] is None
    assert data["posts"][0]["isRepostedByCurrentUser"] is None


def test_get_timeline_global_with_deleted_post_returns_200_without_deleted_post():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = str(auth_response.user_id)

    expected_content = f"Test content {uuid4().hex[:8]}"
    body = PostPostsBody(user_id=auth_response.user_id, content=expected_content)
    create_response = post_posts.sync(client=client, body=body)
    assert create_response is not None
    post_id = str(create_response.post_id)

    delete_response = requests.delete(f"{BASE_URL}/posts/{post_id}", json={"userId": user_id})
    assert delete_response.status_code == 204

    response = requests.get(f"{BASE_URL}/timeline/global")

    assert response.status_code == 200
    data = response.json()
    post_ids = [p["postId"] for p in data["posts"]]
    assert post_id not in post_ids


def test_get_timeline_global_with_liked_post_and_user_id_returns_is_liked_true():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = str(auth_response.user_id)

    expected_content = f"Test content {uuid4().hex[:8]}"
    body = PostPostsBody(user_id=auth_response.user_id, content=expected_content)
    create_response = post_posts.sync(client=client, body=body)
    assert create_response is not None
    post_id = str(create_response.post_id)

    like_response = requests.post(f"{BASE_URL}/posts/{post_id}/likes", json={"userId": user_id})
    assert like_response.status_code == 201

    response = requests.get(f"{BASE_URL}/timeline/global", params={"limit": 1, "userId": user_id})

    assert response.status_code == 200
    data = response.json()
    assert data["posts"][0]["postId"] == post_id
    assert data["posts"][0]["likeCount"] == 1
    assert data["posts"][0]["isLikedByCurrentUser"] is True
    assert data["posts"][0]["isRepostedByCurrentUser"] is False


def test_get_timeline_global_with_limit_and_cursor_returns_paginated_results():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = auth_response.user_id

    post1 = post_posts.sync(
        client=client, body=PostPostsBody(user_id=user_id, content=f"Test content {uuid4().hex[:8]}")
    )
    post2 = post_posts.sync(
        client=client, body=PostPostsBody(user_id=user_id, content=f"Test content {uuid4().hex[:8]}")
    )
    post3 = post_posts.sync(
        client=client, body=PostPostsBody(user_id=user_id, content=f"Test content {uuid4().hex[:8]}")
    )
    assert post1 is not None and post2 is not None and post3 is not None

    page1 = requests.get(f"{BASE_URL}/timeline/global", params={"limit": 2})
    assert page1.status_code == 200
    page1_data = page1.json()
    assert page1_data["limit"] == 2
    assert len(page1_data["posts"]) == 2
    assert page1_data["posts"][0]["postId"] == str(post3.post_id)
    assert page1_data["posts"][1]["postId"] == str(post2.post_id)

    cursor = page1_data["posts"][-1]["postId"]
    page2 = requests.get(f"{BASE_URL}/timeline/global", params={"limit": 2, "afterPostId": cursor})
    assert page2.status_code == 200
    page2_data = page2.json()
    assert page2_data["posts"][0]["postId"] == str(post1.post_id)
