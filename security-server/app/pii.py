from presidio_analyzer import AnalyzerEngine, Pattern, PatternRecognizer
from presidio_analyzer.nlp_engine import NlpEngineProvider
from presidio_anonymizer import AnonymizerEngine
from presidio_anonymizer.entities import OperatorConfig

LABELS = {
    "KR_RRN": "[등록번호 비식별화]",
    "KR_PHONE": "[연락처 비식별화]",
    "KR_BANK_ACCOUNT": "[계좌번호 비식별화]",
    "EMAIL_ADDRESS": "[이메일 비식별화]",
    "USER_NAME": "[근로자 본인]",
    "COMPANY_NAME": "[소속 사업장]",
}

# \b 대신 숫자 경계 사용: 한글도 단어 문자라 "010-1234-5678로"처럼 조사가 붙으면 \b가 성립하지 않음
_recognizers = [
    # 주민등록번호(뒷자리 1-4) + 외국인등록번호(뒷자리 5-8)
    PatternRecognizer(supported_entity="KR_RRN", patterns=[
        Pattern("rrn", r"(?<!\d)\d{6}[-\s]?[1-8]\d{6}(?!\d)", 0.9)]),
    PatternRecognizer(supported_entity="KR_PHONE", patterns=[
        Pattern("mobile", r"(?<!\d)01[016789][-\s]?\d{3,4}[-\s]?\d{4}(?!\d)", 0.85)]),
    # 은행별 하이픈 계좌 형식(3-3-6, 6-2-6, 4-3-6, 3-4-4-2). 마지막 그룹 4자리 이상이라 날짜와 겹치지 않음
    PatternRecognizer(supported_entity="KR_BANK_ACCOUNT", patterns=[
        Pattern("account", r"(?<![\d-])\d{3,6}-\d{2,4}-\d{4,6}(?:-\d{2,3})?(?![\d-])", 0.6)],
        context=["계좌", "은행", "account", "bank"]),
]

_analyzer = AnalyzerEngine(nlp_engine=NlpEngineProvider(nlp_configuration={
    "nlp_engine_name": "spacy",
    "models": [{"lang_code": "en", "model_name": "en_core_web_sm"}],
}).create_engine())
for r in _recognizers:
    _analyzer.registry.add_recognizer(r)

_anonymizer = AnonymizerEngine()
_operators = {e: OperatorConfig("replace", {"new_value": v}) for e, v in LABELS.items()}
# PERSON(spaCy 영어 NER)은 제외: 한국어 문장에서 "주세요" 등을 이름으로 오인. 실명은 알려진 사용자명/사업장명으로만 처리
_base_entities = ["KR_RRN", "KR_PHONE", "KR_BANK_ACCOUNT", "EMAIL_ADDRESS"]


def mask(text: str, user_name: str | None = None, company_name: str | None = None) -> str:
    if not text:
        return text
    ad_hoc, entities = [], list(_base_entities)
    for entity, value in (("USER_NAME", user_name), ("COMPANY_NAME", company_name)):
        if value:
            ad_hoc.append(PatternRecognizer(supported_entity=entity, deny_list=[value], deny_list_score=1.0))
            entities.append(entity)
    results = _analyzer.analyze(text=text, language="en", entities=entities, ad_hoc_recognizers=ad_hoc)
    return _anonymizer.anonymize(text=text, analyzer_results=results, operators=_operators).text
