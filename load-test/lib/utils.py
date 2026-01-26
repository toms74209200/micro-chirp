import random
import string
from typing import Sequence, TypeVar

T = TypeVar("T")


def random_string(length: int) -> str:
    return "".join(random.choice(string.ascii_letters) for i in range(length))


def long_tail_choice(seq: Sequence[T]) -> T:
    """Select an element from a sequence using long-tail distribution (Zipf's law).

    This creates a realistic scenario where early elements are much more likely
    to be selected than later ones, following a power-law distribution.
    The probability of selecting element at rank i is proportional to 1/i.

    This is ideal for simulating social media scenarios where a few posts
    get most of the engagement (likes, views, etc.) while the majority
    receive very little attention.

    Args:
        seq: A non-empty sequence to choose from

    Returns:
        A randomly selected element following Zipf's law distribution

    Example:
        >>> posts = ["viral", "popular", "normal", "unpopular", "ignored"]
        >>> selected = long_tail_choice(posts)  # "viral" most likely, "ignored" least likely
    """
    if not seq:
        raise ValueError("Cannot choose from empty sequence")

    size = len(seq)
    weights = [1 / (i + 1) for i in range(size)]
    index = random.choices(range(size), weights=weights, k=1)[0]

    return seq[index]
