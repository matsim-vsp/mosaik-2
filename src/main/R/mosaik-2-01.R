# add tidyverse and custom color palette which looks like google spread sheets
library(tidyverse)
cbPalette <- c("#4285f4", "#ea4335", "#fbbc04", "#34a853", "#ff6d01", "#46bdc6", "#7baaf7", "#f07b72", "#fcd04f", "#71c287")

#read in palm data
palm_base <- read_csv("/Users/janek/Documents/palm/mosaik-2-01/palm-output/photoshade_6km10m_lod2_av_masked_M01.day2-si-units.xyt.csv") %>%
  mutate(hour = time / 3600)

nox <- palm_base %>%
  select(hour, NO, NO2, NOx) %>%
  pivot_longer(cols = c("NOx", "NO", "NO2" ), names_to = "species", values_to = "concentration") %>%
  mutate(concentration = concentration * 1e6) # convert units into micro grams

no_mean <- nox %>%
  filter(species == "NO") %>%
  group_by(hour) %>%
  summarize(avg = mean(concentration))
print("NO min and max:")
no_mean %>% filter(avg == min(avg))
no_mean %>% filter(avg == max(avg))

no2_mean <- nox %>%
  filter(species == "NO2") %>%
  group_by(hour) %>%
  summarize(avg = mean(concentration))
print("NO2 min and max:")
no2_mean %>% filter(avg == min(avg))
no2_mean %>% filter(avg == max(avg))

p <- ggplot(nox, aes(x = factor(hour), y = concentration, color = factor(species))) +
  geom_boxplot(outlier.alpha = 0.3, outlier.shape = NA) +
  ylim(0, 1e2) +
  ggtitle("NOx Concentrations") +
  labs(color = "Species") +
  xlab("Hour") +
  ylab("Concentration [\u00B5g/m3]") +
  scale_fill_manual(values = cbPalette) +
  scale_color_manual(values = cbPalette) +
  theme_light() +
  theme(text = element_text(size = 10))
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-01/data-files-nextcloud/r-output/nox-aggregated.pdf", width = 210, height = 118, units = "mm", dpi = 300)

