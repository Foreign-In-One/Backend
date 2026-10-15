from fastapi import FastAPI
from pydantic import BaseModel

from app import guard, pii
from app.normalize import normalize

app = FastAPI(title="PayCycle AI security server")


class PreRequest(BaseModel):
    question: str
    context: str = ""
    userName: str | None = None
    companyName: str | None = None


class PreResponse(BaseModel):
    blocked: bool
    reason: str | None = None
    sanitizedQuestion: str = ""
    sanitizedContext: str = ""


class PostRequest(BaseModel):
    responseText: str
    canaryToken: str


class PostResponse(BaseModel):
    blocked: bool
    safeText: str


@app.post("/guard/pre", response_model=PreResponse)
async def guard_pre(req: PreRequest) -> PreResponse:
    question = normalize(req.question)
    reason = await guard.check_input(question)
    if reason:
        return PreResponse(blocked=True, reason=reason)
    return PreResponse(
        blocked=False,
        sanitizedQuestion=pii.mask(question),
        sanitizedContext=pii.mask(req.context, req.userName, req.companyName),
    )


@app.post("/guard/post", response_model=PostResponse)
async def guard_post(req: PostRequest) -> PostResponse:
    if await guard.check_output(req.responseText, req.canaryToken):
        return PostResponse(blocked=True, safeText=guard.SAFE_TEXT)
    return PostResponse(blocked=False, safeText=req.responseText)


@app.get("/health")
def health():
    return {"status": "ok"}
