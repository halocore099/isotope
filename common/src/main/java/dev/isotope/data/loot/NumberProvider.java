package dev.isotope.data.loot;

/**
 * Represents a number provider for loot table rolls, counts, etc.
 * Mirrors Minecraft's number provider system.
 */
public sealed interface NumberProvider permits
        NumberProvider.Constant,
        NumberProvider.Uniform,
        NumberProvider.Binomial {

    /**
     * Get a sample value from this provider.
     */
    float sample(java.util.Random random);

    /**
     * Get the minimum possible value.
     */
    float getMin();

    /**
     * Get the maximum possible value.
     */
    float getMax();

    /**
     * Constant value provider.
     */
    record Constant(float value) implements NumberProvider {
        @Override
        public float sample(java.util.Random random) {
            return value;
        }

        @Override
        public float getMin() {
            return value;
        }

        @Override
        public float getMax() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf((int) value);
        }
    }

    /**
     * Uniform distribution between min and max.
     */
    record Uniform(float min, float max) implements NumberProvider {
        @Override
        public float sample(java.util.Random random) {
            return min + random.nextFloat() * (max - min);
        }

        @Override
        public float getMin() {
            return min;
        }

        @Override
        public float getMax() {
            return max;
        }

        @Override
        public String toString() {
            return (int) min + "-" + (int) max;
        }
    }

    /**
     * Binomial distribution with n trials and probability p.
     */
    record Binomial(int n, float p) implements NumberProvider {
        @Override
        public float sample(java.util.Random random) {
            int result = 0;
            for (int i = 0; i < n; i++) {
                if (random.nextFloat() < p) {
                    result++;
                }
            }
            return result;
        }

        @Override
        public float getMin() {
            return 0;
        }

        @Override
        public float getMax() {
            return n;
        }

        @Override
        public String toString() {
            return "binomial(" + n + ", " + p + ")";
        }
    }

    /**
     * Create a constant provider.
     */
    static NumberProvider constant(float value) {
        return new Constant(value);
    }

    /**
     * Create a uniform provider.
     */
    static NumberProvider uniform(float min, float max) {
        return new Uniform(min, max);
    }

    /**
     * Create a binomial provider.
     */
    static NumberProvider binomial(int n, float p) {
        return new Binomial(n, p);
    }
}
