# add tidyverse and custom color palette which looks like google spread sheets
library(tidyverse)
cbPalette <- c("#4285f4", "#ea4335", "#fbbc04", "#34a853", "#ff6d01", "#46bdc6", "#7baaf7", "#f07b72", "#fcd04f", "#71c287")

# linear fit stuff
# read in smoothed matsim data
matsim_smoothed <- read_csv("C:/Users/janek/Documents/work/palm/berlin_with_geometry_attributes/output/berlin-with-geometry-attributes.output_smoothed_rastered.xyt.csv")
# read in base palm data from base case
palm_base <- read_csv("C:/Users/janek/Documents/work/palm/berlin_with_geometry_attributes/palm-output/photoshade_6km10m_lod2_av_masked_M01.day2-si-units.xyt.csv") %>%
  mutate(NOx = NO2 + NO)

matsim_palm_base <- palm_base %>%
  inner_join(matsim_smoothed, by = c("x", "y", "time"), suffix = c(".palm", ".matsim")) %>%
  mutate(hour = time / 3600) %>%
  filter(hour > 5) %>%
  filter(hour < 18)
matsim_palm_base_nox <- matsim_palm_base %>%
  select(hour, x, y, NOx.matsim, NOx.palm)

#Our transformation function
scaleFUN <- function(x) sprintf("%.2f", x)

p <- ggplot(matsim_palm_base_nox, aes(x = NOx.matsim, y = NOx.palm)) +
  geom_point(alpha = 0.5, shape = ".") +
  geom_smooth(method = "lm") +
  ylim(0, 0.0005) +
  xlim(0, 0.01) +
  ggtitle("NOx Konzentrationen [g/m3] MATSim vs. PALM") +
  facet_wrap(vars(hour)) +
  scale_fill_manual(values = cbPalette) +
  scale_x_continuous(labels = scaleFUN) +
  theme_light() +
  theme(text = element_text(size = 16))
ggsave(plot = p, filename = "linear-fit-nox.png", width = 32, height = 18, units = "cm", dpi = 300)


berlin <- read_csv("C:/Users/janek/Documents/work/berlin-roadpricing/output-rp-time-berlin-100/photoshade_6km10m_berlin_av_masked_M01.day2-si-units.xyt.csv") %>%
  mutate(NOx = NO2 + NO)
city <- read_csv("C:/Users/janek/Documents/work/berlin-roadpricing/output-rp-time-hundek-100/photoshade_6km10m_hundek_av_masked_M01.day2-si-units.xyt.csv") %>%
  mutate(NOx = NO2 + NO)

# join datasets on time, x, y
# sufixes work like in this issue: https://github.com/tidyverse/dplyr/issues/6553
concentrations <- base %>%
  inner_join(berlin, by = c("time", "x", "y"), suffix = c("", ".berlin")) %>%
  inner_join(city, by = c("time", "x", "y"), suffix = c(".base", ".city")) %>%
  pivot_longer(cols = c(ends_with(".berlin"), ends_with(".base"), ends_with(".city"))) %>%
  mutate(hour = time / 3600)

# use first twelve hours, because this increases readability of the plot and we wanted to mitigate the morning peak
nox_morning <- concentrations %>%
  filter(grepl("NOx", name))

p <- ggplot(nox_morning, aes(x = factor(hour), y = value, color = factor(name))) +
  geom_boxplot(outlier.alpha = 0.3, outlier.shape = ".") +
  ylim(0, 5e-4) +
  ggtitle("PALM Konzentrationen NOx [g/m3]") +
  labs(fill = "Szenario") +
  xlab("Stunde") +
  scale_fill_manual(values = cbPalette) +
  theme_light() +
  theme(text = element_text(size = 16))
ggsave(plot = p, filename = "compare_concentrations_nox.png", width = 32, height = 18, units = "cm", dpi = 300)

pm <- concentrations %>%
  filter(grepl("PM10", name))