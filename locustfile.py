import random
import json
from locust import HttpUser, task, between

VALID_KEYS = [
    "dev-key-token", "dev-key-fixed", "dev-key-sliding", 
    "dev-key-leaky", "dev-key-business", "key-acme-dashboard", 
    "key-acme-api", "key-beta-dashboard", "key-beta-api", 
    "key-enterprise-dashboard", "key-enterprise-api", "dev-key-dynamic"
]

class ApiGatewayUser(HttpUser):
    wait_time = between(1, 3)

    def on_start(self):
        self.api_key = random.choice(VALID_KEYS)
        self.guid = f"test-{random.randint(1000, 9999)}"
        self.ip = f"{random.randint(1, 255)}.{random.randint(1, 255)}.{random.randint(1, 255)}.{random.randint(1, 255)}"

        self.target_gateway = random.choice(["http://gateway1:8080", "http://gateway2:8080"])

    def _get_headers(self, custom_key=None, custom_ip=None):
        return {
            "X-API-KEY": custom_key or self.api_key,
            "X-Forwarded-For": custom_ip or self.ip,
            "Content-Type": "application/json"
        }

    # --- 1. NORMAL TRAFFIC ---
    @task(6)
    def get_notes(self):
        with self.client.get(f"{self.target_gateway}/api/{self.guid}/notes", headers=self._get_headers(), catch_response=True) as response:
            if response.status_code in [200, 429, 403]:
                response.success()

    @task(2)
    def create_note(self):
        payload_dict = {"id": str(random.randint(1000, 9999)), "content": "Hello from Locust"}
        with self.client.post(f"{self.target_gateway}/api/{self.guid}/notes", json=payload_dict, headers=self._get_headers(), catch_response=True) as response:
            if response.status_code in [200, 201, 429, 403]:
                response.success()

    # --- 2. BURST TRAFFIC ---
    @task(1)
    def burst_traffic(self):
        for _ in range(15):
            with self.client.get(f"{self.target_gateway}/api/{self.guid}/notes", headers=self._get_headers(), catch_response=True) as response:
                if response.status_code in [200, 429, 403]:
                    response.success()

    # --- 3. ABUSIVE PATTERN ---
    @task(1)
    def abuse_invalid_key(self):
        headers = self._get_headers(custom_key="invalid-key-123")
        for _ in range(6):
            with self.client.get(f"{self.target_gateway}/api/{self.guid}/notes", headers=headers, catch_response=True) as response:
                if response.status_code in [401, 403]:
                    response.success()
                else:
                    response.failure(f"Expected block, got {response.status_code}")

    # --- 4. BOT DETECTOR TRIGGER ---
    @task(1)
    def bot_attack(self):
        bot_ip = f"10.99.99.{random.randint(1, 250)}"
        headers = self._get_headers(custom_ip=bot_ip)
        for _ in range(55):
            with self.client.get(f"{self.target_gateway}/api/{self.guid}/notes", headers=headers, catch_response=True) as response:
                if response.status_code in [200, 429, 403]:
                    response.success()