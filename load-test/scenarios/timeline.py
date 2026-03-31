import os

from locust import HttpUser, between, events, task

from lib.utils import long_tail_choice, random_string
from openapi_gen.micro_chirp_api_client.api.auth import post_auth_login
from openapi_gen.micro_chirp_api_client.api.posts import post_posts
from openapi_gen.micro_chirp_api_client.client import Client
from openapi_gen.micro_chirp_api_client.models.post_posts_body import PostPostsBody


class GetGlobalTimelineAPI(HttpUser):
    """Test GET /timeline/global API

    Simulates users browsing the global timeline.
    Uses a shared pool of user IDs to test the isLikedByCurrentUser / isRepostedByCurrentUser
    fields by passing userId as a query parameter.

    Pool size can be configured via TIMELINE_USER_POOL_SIZE (default: 50).
    """

    wait_time = between(1, 5)
    user_pool = []

    @task(3)
    def get_global_timeline(self):
        self.client.get("/timeline/global", params={"limit": 20})

    @task(1)
    def get_global_timeline_with_user(self):
        if not self.user_pool:
            self.get_global_timeline()
            return
        user_id = long_tail_choice(self.user_pool)
        self.client.get("/timeline/global", params={"limit": 20, "userId": user_id})


class GetGlobalTimelineWithPaginationAPI(HttpUser):
    """Test GET /timeline/global API with cursor-based pagination

    Simulates users scrolling through multiple pages of the global timeline.
    """

    wait_time = between(1, 3)

    @task
    def get_global_timeline_paginated(self):
        response = self.client.get("/timeline/global", params={"limit": 20})
        if response.status_code != 200:
            return

        data = response.json()
        posts = data.get("posts", [])
        if not posts:
            return

        cursor = posts[-1]["postId"]
        self.client.get(
            "/timeline/global",
            params={"limit": 20, "afterPostId": cursor},
            name="/timeline/global?afterPostId=[cursor]",
        )


class GetUserTimelineAPI(HttpUser):
    """Test GET /timeline/users/{userId} API

    Simulates users viewing another user's profile timeline.
    Uses a shared pool of user IDs with posts so that each request
    returns meaningful data.

    Pool size can be configured via TIMELINE_USER_POOL_SIZE (default: 50).
    """

    wait_time = between(1, 5)
    user_pool = []

    def on_start(self):
        client = Client(base_url=self.host)
        auth_response = post_auth_login.sync(client=client)
        self.user_id = str(auth_response.user_id)

    @task(3)
    def get_user_timeline(self):
        if not self.user_pool:
            return
        user_id = long_tail_choice(self.user_pool)
        self.client.get(
            f"/timeline/users/{user_id}",
            params={"limit": 20},
            name="/timeline/users/[userId]",
        )

    @task(1)
    def get_user_timeline_with_current_user(self):
        if not self.user_pool:
            return
        user_id = long_tail_choice(self.user_pool)
        self.client.get(
            f"/timeline/users/{user_id}",
            params={"limit": 20, "currentUserId": self.user_id},
            name="/timeline/users/[userId]?currentUserId=[currentUserId]",
        )


@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    """Create a shared pool of users with posts at the start of the test"""
    pool_size = int(os.getenv("TIMELINE_USER_POOL_SIZE", "50"))
    posts_per_user = int(os.getenv("TIMELINE_POSTS_PER_USER", "100"))

    host = environment.host or "http://localhost:8080"
    client = Client(base_url=host)

    for i in range(pool_size):
        auth_response = post_auth_login.sync(client=client)
        user_id = auth_response.user_id

        for j in range(posts_per_user):
            body = PostPostsBody(
                user_id=user_id,
                content=f"Timeline load test post user={i + 1} post={j + 1} {random_string(10)}",
            )
            post_posts.sync(client=client, body=body)

        GetGlobalTimelineAPI.user_pool.append(str(user_id))
        GetUserTimelineAPI.user_pool.append(str(user_id))
