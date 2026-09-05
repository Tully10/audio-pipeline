import re


def extract_mermaid(text: str) -> str:
    """Extract mermaid code block from LLM response."""
    match = re.search(r"```(?:mermaid)?\n(.*?)```", text, re.DOTALL)
    if match:
        return match.group(1).strip()
    return text.strip()
