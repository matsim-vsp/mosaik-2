# add tidyverse and custom color palette which looks like google spread sheets
library(tidyverse)
library(clock)
cbPalette <- c("#4285f4","#fbbc04", "#ea4335",  "#34a853", "#ff6d01", "#46bdc6", "#7baaf7", "#f07b72", "#fcd04f", "#71c287")

data <- read_csv("/Users/janek/Documents/palm/berlin_with_geometry_attributes/output/diff_algorithm_01.csv")

nox <- data %>%
  select(key, "nox_avg", "nox_sng")

nox_ratio <- nox %>%
  mutate(ratio = nox_sng / nox_avg)

p <- ggplot(nox, aes(nox_avg, nox_sng)) +
  geom_point(shape = ".", alpha = 0.8) +
  xlim(0, 0.5) +
  ylim(0, 0.5) +
  #geom_abline(slope = 1, intercept = 0) +
  ggtitle("Comparison Emission Events (time, link, vehicle) by Calculation Method") +
  xlab("Average over Link [g]") +
  ylab("Stop and Go [g]") +
  scale_fill_manual(values = cbPalette) +
  theme_light()
#ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-02/data-files-nextcloud/r-output/comparision.pdf", width = 220, height = 118, units = "mm")
#ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-02/data-files-nextcloud/r-output/line-toll.png", width = 220, height = 118, units = "mm")
p

p <- ggplot(nox_ratio, aes(ratio)) +
  geom_histogram(binwidth = 0.02) +
  xlim(-1, 3)+
  ggtitle("Distribution of ratio Computation Method Average / Stop and Go") +
  scale_fill_manual(values = cbPalette) +
  scale_color_manual(values = cbPalette) +
  theme_light()
p