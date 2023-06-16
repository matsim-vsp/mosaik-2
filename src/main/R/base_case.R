library(tidyverse)
cbPalette <- c("#4285f4", "#ea4335", "#fbbc04", "#34a853", "#ff6d01", "#46bdc6", "#7baaf7", "#f07b72", "#fcd04f", "#71c287")

emission_per_meter <- read_csv("hourly-emissions-per-meter.csv")
emission_sums <- read_csv("C:/Users/janek/Documents/work/berlin-roadpricing/output-rp-time-berlin-100/hourly-matsim-emissions-in-filter.csv")

ggplot(emission_sums, aes(time, sum), color = species) +
  geom_line() +
  facet_wrap(vars(species)) +
  scale_color_manual(values = cbPalette) +
  theme_light() +
  ggtitle("Sums of emissions over time")

pm <- emission_per_meter %>%
  filter(species == "PM") %>%
  mutate(scaled = value * 10)
ggplot(pm, aes(scaled)) +
  geom_histogram(binwidth = 0.00001) +
  xlim(0, 0.001) +
  scale_color_manual(values = cbPalette) +
  theme_light() +
  ggtitle("Histogram average emissions per meter over time")

nox <- emission_per_meter %>%
  filter(species == "NOx") %>%
  mutate(scaled = value * 10)
ggplot(nox, aes(scaled)) +
  geom_histogram(binwidth = 0.001) +
  xlim(0, 0.1) +
  scale_color_manual(values = cbPalette) +
  theme_light() +
  ggtitle("Histogram average emissions per meter over time")
