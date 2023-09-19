# add tidyverse and custom color palette which looks like google spread sheets
library(tidyverse)
library(clock)
cbPalette <- c("#4285f4","#fbbc04", "#ea4335",  "#34a853", "#ff6d01", "#46bdc6", "#7baaf7", "#f07b72", "#fcd04f", "#71c287")

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

#------------ calculate these stats here, because we cite this in the paper ------------
nox_quantiles <- nox %>%
  filter(species == "NOx") %>%
  filter(hour == 7) %>%
  group_by(hour) %>%
  reframe(qs = quantile(concentration, c(0.25, 0.50, 0.75)), prob = c(0.25, 0.50, 0.75))

nox_median <- nox %>%
  filter(species == "NOx") %>%
  group_by(hour) %>%
  summarize(avg = median(concentration))
print("NO2 min and max:")
nox_median %>% filter(avg == min(avg))
nox_median %>% filter(avg == max(avg))

p <- ggplot(nox, aes(x = factor(hour), y = concentration, color = factor(species))) +
  geom_boxplot(outlier.shape = NA) +
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
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-01/data-files-nextcloud/r-output/nox-aggregated.png", width = 210, height = 118, units = "mm", dpi = 300)

#-------------- plot curb data ----------------
curb_data_115 <- read_delim(c(
  "/Users/janek/Documents/writing/mosaik-2-01/data-files-nextcloud/monitoring-stations/ber_mc115_20170710-20170714.csv",
  "/Users/janek/Documents/writing/mosaik-2-01/data-files-nextcloud/monitoring-stations/ber_mc115_20170717-20170721.csv",
  "/Users/janek/Documents/writing/mosaik-2-01/data-files-nextcloud/monitoring-stations/ber_mc115_20170724-20170728.csv"
), delim = ";", col_types = list(col_datetime("%d.%m.%Y %H:%M"), col_double(), col_double(), col_double())) %>%
  mutate(hour = get_hour(Date)) %>%
  pivot_longer(cols = c("NOx", "NO", "NO2" ), names_to = "species", values_to = "concentration")

curb_data_010 <- read_delim(c(
  "/Users/janek/Documents/writing/mosaik-2-01/data-files-nextcloud/monitoring-stations/ber_mc010_20170710-20170714.csv",
  "/Users/janek/Documents/writing/mosaik-2-01/data-files-nextcloud/monitoring-stations/ber_mc010_20170717-20170721.csv",
  "/Users/janek/Documents/writing/mosaik-2-01/data-files-nextcloud/monitoring-stations/ber_mc010_20170724-20170728.csv"
), delim = ";", col_types = list(col_datetime("%d.%m.%Y %H:%M"), col_double(), col_double(), col_double())) %>%
  mutate(hour = get_hour(Date)) %>%
  pivot_longer(cols = c("NOx", "NO", "NO2", "PM10"), names_to = "species", values_to = "concentration")

p <- ggplot(curb_data_115, aes(x = factor(hour), y = concentration, color = factor(species))) +
  geom_boxplot() +
  ggtitle("Nitrogen Concentrations 115") +
  theme_light() +
  scale_fill_manual(values = cbPalette) +
  scale_color_manual(values = cbPalette) +
  theme_light() +
  theme(text = element_text(size = 10))
p

p <- ggplot(curb_data_010, aes(x = factor(hour), y = concentration, color = factor(species))) +
  geom_boxplot() +
  ggtitle("Nitrogen Concentrations 010") +
  theme_light() +
  scale_fill_manual(values = cbPalette) +
  scale_color_manual(values = cbPalette) +
  theme_light() +
  theme(text = element_text(size = 10))
p

#-------------- plot joined nox data ----------------
curb_nox_010 <- curb_data_010 %>%
  select(hour, species, concentration) %>%
  filter(species == "NOx") %>%
  mutate(name = "010 (north)")
curb_nox_115 <- curb_data_115 %>%
  select(hour, species, concentration) %>%
  filter(species == "NOx") %>%
  mutate(name = "115 (south)")
palm_nox_115 <- palm_base %>%
  select(hour, x, y, NOx) %>%
  pivot_longer(cols = "NOx", names_to = "species", values_to = "concentration") %>%
  filter(x > 386854 - 50 & x < 386854 + 50 & y > 5818691 - 50 & y < 5818691 + 50) %>%
  filter(hour < 24) %>%
  select(hour, species, concentration) %>%
  mutate(concentration = concentration * 1e6)%>%
  mutate(name = "115 simulated")
palm_nox_010 <- palm_base %>%
  select(hour, x, y, NOx) %>%
  pivot_longer(cols = "NOx", names_to = "species", values_to = "concentration") %>%
  filter(x > 388055 - 50 & x < 388055 + 50 & y > 5822705 - 50 & y < 5822705 + 50) %>%
  filter(hour < 24) %>%
  select(hour, species, concentration) %>%
  mutate(concentration = concentration * 1e6)%>%
  mutate(name = "010 simulated")
palm_nox <- palm_base %>%
  select(hour, x, y, NOx) %>%
  pivot_longer(cols = "NOx", names_to = "species", values_to = "concentration") %>%
  #filter(x > 388055 - 50 & x < 388055 + 50 & y > 5822705 - 50 & y < 5822705 + 50) %>%
  filter(hour < 24) %>%
  select(hour, species, concentration) %>%
  mutate(concentration = concentration * 1e6)%>%
  mutate(name = "all simulated")
joined_nox <- palm_nox_115 %>% full_join(palm_nox_010) %>% full_join(curb_nox_010) %>% full_join(curb_nox_115) %>% full_join(palm_nox)

p <- ggplot(joined_nox, aes(x = factor(hour), y = concentration, color = factor(name))) +
  geom_boxplot(outlier.shape = NA) +
  ylim(0, 3e2) +
  ggtitle("NOx concentrations at monitoring stations and in simulation") +
  labs(color = "Series") +
  xlab("Hour") +
  ylab("Concentration [\u00B5g/m3]") +
  theme_light() +
  scale_fill_manual(values = cbPalette) +
  scale_color_manual(values = cbPalette) +
  theme_light() +
  theme(text = element_text(size = 10))
p
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-01/data-files-nextcloud/r-output/nox-comparison.png", width = 210, height = 118, units = "mm", dpi = 300)
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-01/data-files-nextcloud/r-output/nox-comparison.pdf", width = 210, height = 118, units = "mm", dpi = 300)

curb_pm10 <- curb_data_010 %>%
  select(hour, species, concentration) %>%
  filter(species == "PM10") %>%
  mutate(name = "010")
palm_pm10 <- palm_base %>%
  select(hour, x, y, PM10) %>%
  pivot_longer(cols = "PM10", names_to = "species", values_to = "concentration") %>%
  #filter(x > 386854 - 100 & x < 386854 + 100 & y > 5818691 - 100 & y < 5818691 + 100) %>%
  filter(hour < 24) %>%
  select(hour, species, concentration) %>%
  mutate(concentration = concentration * 1e6)%>%
  mutate(name = "simulated")
joined_pm10 <- palm_pm10 %>% full_join(curb_pm10)

p <- ggplot(joined_pm10, aes(x = factor(hour), y = concentration, color = factor(name))) +
  geom_boxplot(outlier.shape = NA) +
  ylim(0, 50) +
  ggtitle("PM10 Concentrations - Comparision Montoring Station and Simulation") +
  theme_light() +
  scale_fill_manual(values = cbPalette) +
  scale_color_manual(values = cbPalette) +
  theme_light() +
  theme(text = element_text(size = 10))
p



