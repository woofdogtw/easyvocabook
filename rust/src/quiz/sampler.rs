use crate::db::types::WordEntry;

/// Weighted random sampler. Returns the index of the selected entry.
/// Returns `None` if the slice is empty.
pub fn weighted_pick(entries: &[WordEntry], rng_seed: u64) -> Option<usize> {
    if entries.is_empty() {
        return None;
    }
    let weights: Vec<f64> = entries.iter().map(|e| e.quiz_weight()).collect();
    let total: f64 = weights.iter().sum();
    let mut target = lcg_float(rng_seed) * total;
    for (i, w) in weights.iter().enumerate() {
        target -= w;
        if target <= 0.0 {
            return Some(i);
        }
    }
    Some(entries.len() - 1)
}

/// Simple LCG PRNG that produces a float in [0, 1).
fn lcg_float(seed: u64) -> f64 {
    const A: u64 = 6364136223846793005;
    const C: u64 = 1442695040888963407;
    let next = seed.wrapping_mul(A).wrapping_add(C);
    (next >> 11) as f64 / (1u64 << 53) as f64
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::types::WordEntry;

    fn entry(id: i64, practice_count: i64, correct_count: i64) -> WordEntry {
        WordEntry {
            id,
            word: format!("w{id}"),
            reading: None,
            meaning: "test".into(),
            part_of_speech: None,
            note: None,
            language: "en".into(),
            practice_count,
            correct_count,
            created_at: 0,
            practiced_at: None,
            meanings: vec![],
            forms: vec![],
            sentences: vec![],
        }
    }

    #[test]
    fn new_word_weight_3() {
        let e = entry(1, 0, 0);
        assert_eq!(e.quiz_weight(), 3.0);
    }

    #[test]
    fn perfect_word_weight_1() {
        let e = entry(1, 10, 10);
        assert_eq!(e.quiz_weight(), 1.0);
    }

    #[test]
    fn all_wrong_weight_4() {
        let e = entry(1, 5, 0);
        assert_eq!(e.quiz_weight(), 4.0);
    }

    #[test]
    fn empty_pool_returns_none() {
        assert_eq!(weighted_pick(&[], 42), None);
    }

    #[test]
    fn single_entry_always_picked() {
        let entries = vec![entry(1, 0, 0)];
        for seed in 0..100u64 {
            assert_eq!(weighted_pick(&entries, seed), Some(0));
        }
    }
}
