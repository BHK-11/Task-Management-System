import os
import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient

from main import PlanStep, WorkPlanDraft, WorkPlanRequest, WorkPlanStep, app, topological_order


class PlannerContractTest(unittest.TestCase):
    def test_topological_order_places_prerequisite_first(self):
        steps = [
            PlanStep(actionType="CREATE_TASK", title="Second", dependencies=[1]),
            PlanStep(actionType="CREATE_TASK", title="First"),
        ]
        self.assertEqual(topological_order(steps), [1, 0])

    def test_cycle_is_rejected(self):
        steps = [
            PlanStep(actionType="CREATE_TASK", title="A", dependencies=[1]),
            PlanStep(actionType="CREATE_TASK", title="B", dependencies=[0]),
        ]
        with self.assertRaisesRegex(ValueError, "Cyclic"):
            topological_order(steps)

    def test_invalid_dependency_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Invalid"):
            topological_order([PlanStep(actionType="CREATE_TASK", title="A", dependencies=[2])])


class PlannerEndpointTest(unittest.TestCase):
    def test_endpoint_rejects_missing_service_authentication(self):
        with patch.dict(os.environ, {"TASKOS_AI_SERVICE_TOKEN": "t" * 32}):
            response = TestClient(app).post(
                "/v1/plan",
                json={"instruction": "Create a task", "timezone": "Asia/Kolkata", "today": "2026-10-26"},
            )
        self.assertEqual(response.status_code, 401)

    def test_missing_provider_configuration_returns_service_unavailable(self):
        with patch.dict(os.environ, {
            "TASKOS_AI_SERVICE_TOKEN": "t" * 32,
            "OPENAI_API_KEY": "",
            "OPENAI_MODEL": "",
        }):
            response = TestClient(app).post(
                "/v1/plan",
                headers={"X-TaskOS-Service-Token": "t" * 32},
                json={"instruction": "Create a task", "timezone": "Asia/Kolkata", "today": "2026-10-26"},
            )
        self.assertEqual(response.status_code, 503)
        self.assertEqual(response.json()["detail"], "AI provider is not configured")

    def test_private_mode_without_local_model_returns_service_unavailable(self):
        with patch.dict(os.environ, {"TASKOS_AI_SERVICE_TOKEN": "t" * 32, "OLLAMA_MODEL": ""}):
            response = TestClient(app).post(
                "/v1/work-plan",
                headers={"X-TaskOS-Service-Token": "t" * 32},
                json={"instruction": "Keep this private", "timezone": "Asia/Kolkata",
                      "today": "2026-10-26", "mode": "PRIVATE", "allowCloudProcessing": False},
            )
        self.assertEqual(response.status_code, 503)
        self.assertIn("requires an Ollama model", response.json()["detail"])

    def test_oversized_timezone_is_rejected_at_the_contract(self):
        with patch.dict(os.environ, {"TASKOS_AI_SERVICE_TOKEN": "t" * 32}):
            response = TestClient(app).post(
                "/v1/plan",
                headers={"X-TaskOS-Service-Token": "t" * 32},
                json={"instruction": "Create a task", "timezone": "x" * 65, "today": "2026-10-26"},
            )
        self.assertEqual(response.status_code, 422)


class WorkPlannerContractTest(unittest.TestCase):
    def test_document_extraction_returns_cited_text_after_service_authentication(self):
        with patch.dict(os.environ, {"TASKOS_AI_SERVICE_TOKEN": "t" * 32}):
            response = TestClient(app).post("/v1/documents/extract/txt",
                    headers={"X-TaskOS-Service-Token": "t" * 32}, content=b"Project deadline: Friday")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["segments"][0]["locator"], "Line 1")
        self.assertEqual(response.json()["segments"][0]["text"], "Project deadline: Friday")

    def test_document_extraction_endpoint_requires_service_authentication(self):
        with patch.dict(os.environ, {"TASKOS_AI_SERVICE_TOKEN": "t" * 32}):
            response = TestClient(app).post("/v1/documents/extract/txt", content=b"Private notes")
        self.assertEqual(response.status_code, 401)

    def test_model_catalog_api_requires_service_authentication(self):
        with patch.dict(os.environ, {"TASKOS_AI_SERVICE_TOKEN": "t" * 32}):
            response = TestClient(app).get("/v1/models")
        self.assertEqual(response.status_code, 401)

    def test_accepts_long_multilingual_instruction_and_typed_deliverable_jobs(self):
        request = WorkPlanRequest(instruction="कृपया मेरी Java assignment की योजना बनाओ। " * 300,
                                  timezone="Asia/Kolkata", today="2026-10-26")
        self.assertGreater(len(request.instruction), 4_000)
        plan = WorkPlanDraft(summary="Study plan", detectedLanguage="mixed", clarifications=[], steps=[
            WorkPlanStep(actionType="GENERATE_DOCX", title="Study notes", content="Inheritance overview"),
            WorkPlanStep(actionType="DRAFT_EMAIL", title="Teacher email", content="Dear teacher", dependencies=[0]),
        ])
        self.assertEqual(topological_order(plan.steps), [0, 1])

    def test_work_plan_rejects_cycles_and_unknown_tools(self):
        with self.assertRaisesRegex(ValueError, "Cyclic"):
            WorkPlanDraft(summary="bad", detectedLanguage="en", clarifications=[], steps=[
                WorkPlanStep(actionType="CREATE_TASK", title="A", dependencies=[1]),
                WorkPlanStep(actionType="CREATE_TASK", title="B", dependencies=[0]),
            ])
        with self.assertRaises(Exception):
            WorkPlanStep(actionType="SEND_WHATSAPP_MESSAGE", title="Send", content="Hello")

    def test_work_plan_api_requires_service_authentication(self):
        with patch.dict(os.environ, {"TASKOS_AI_SERVICE_TOKEN": "t" * 32}):
            response = TestClient(app).post(
                "/v1/work-plan",
                json={"instruction": "Make a study plan", "timezone": "Asia/Kolkata", "today": "2026-10-26"},
            )
        self.assertEqual(response.status_code, 401)

    def test_artifact_api_requires_service_authentication(self):
        with patch.dict(os.environ, {"TASKOS_AI_SERVICE_TOKEN": "t" * 32}):
            response = TestClient(app).post("/v1/artifacts/docx", json={"title": "Notes", "content": "Some notes"})
        self.assertEqual(response.status_code, 401)


if __name__ == "__main__":
    unittest.main()
