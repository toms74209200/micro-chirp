import os
from locust import HttpUser, task, between, events

from lib.utils import random_string, long_tail_choice
from openapi_gen.micro_chirp_api_client.api.auth import post_auth_login
from openapi_gen.micro_chirp_api_client.api.posts import post_posts
from openapi_gen.micro_chirp_api_client.api.reposts import post_reposts
from openapi_gen.micro_chirp_api_client.client import Client
from openapi_gen.micro_chirp_api_client.models.post_posts_body import PostPostsBody
from openapi_gen.micro_chirp_api_client.models.post_reposts_body import PostRepostsBody


class RepostPostAPI(HttpUser):
    """Test POST /posts/{postId}/reposts API

    This test creates a shared pool of posts at the start of the test.
    Each user randomly selects a post from the pool using long-tail distribution
    (Zipf's law), simulating realistic social media behavior where a few posts
    get viral engagement while most posts receive little attention.

    The number of posts in the pool can be configured via the REPOST_POST_POOL_SIZE
    environment variable (default: 10).
    """

    wait_time = between(1, 5)
    post_pool = []

    def on_start(self):
        client = Client(base_url=self.host)
        auth_response = post_auth_login.sync(client=client)
        self.user_id = str(auth_response.user_id)

    @task
    def repost_post(self):
        if not self.post_pool:
            return

        post_id = long_tail_choice(self.post_pool)
        self.client.post(
            f"/posts/{post_id}/reposts",
            json={"userId": self.user_id},
        )


class UnrepostPostAPI(HttpUser):
    """Test DELETE /posts/{postId}/reposts API"""

    wait_time = between(1, 5)

    def on_start(self):
        self.api_client = Client(base_url=self.host)
        auth_response = post_auth_login.sync(client=self.api_client)
        self.user_id = auth_response.user_id

        body = PostPostsBody(user_id=auth_response.user_id, content=f"Test post for unrepost {random_string(10)}")
        post_response = post_posts.sync(client=self.api_client, body=body)
        self.post_id = post_response.post_id

    @task
    def unrepost_post(self):
        body = PostRepostsBody(user_id=self.user_id)
        post_reposts.sync(post_id=self.post_id, client=self.api_client, body=body)

        self.client.delete(
            f"/posts/{self.post_id}/reposts",
            json={"userId": str(self.user_id)},
        )


@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    """Create a shared pool of posts at the start of the test"""
    pool_size = int(os.getenv("REPOST_POST_POOL_SIZE", "10"))

    host = environment.host or "http://localhost:8080"

    client = Client(base_url=host)

    auth_response = post_auth_login.sync(client=client)
    user_id = auth_response.user_id

    for i in range(pool_size):
        body = PostPostsBody(
            user_id=user_id, content=f"Shared post for repost load test {i + 1}/{pool_size} {random_string(10)}"
        )
        post_response = post_posts.sync(client=client, body=body)
        RepostPostAPI.post_pool.append(str(post_response.post_id))
