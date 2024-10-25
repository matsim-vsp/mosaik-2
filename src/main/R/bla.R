# Load the ncdf4 package
library(ncdf4)
library(ggplot2)
library(tidyr) # For reshaping the data frame

print("Connecting to file.")

# Specify the path to your NetCDF file
# Make sure to replace this with the actual path to your NetCDF file
netcdfFilePath <- "/scratch/projects/nik00072/iop_runs/already_finished/VALM02_v2/OUTPUT_TRIM/MASK/N03/VALM02_v2_masked_N03_M01.001.nc"

# Open the NetCDF file
ncfile <- nc_open(netcdfFilePath)# Read the kc_NO2 variable for a specific time step and ku_above_surf layer

print("Opened file. extractin slices and x and y dim.")
# Adjust the indices as needed for your specific time step and layer
kc_NO2_slice <- ncvar_get(ncfile, "kc_NO2")[,,1,1] # For example, first layer, first time step# Read the x and y dimension values

x_dim <- ncvar_get(ncfile, "x")
y_dim <- ncvar_get(ncfile, "y")

print("converting dims into dataframe")
# Create a data frame from the matrix, including x and y coordinates
kc_NO2_df <- expand.grid(x = x_dim, y = y_dim, KEEP.OUT.ATTRS = FALSE)
kc_NO2_df$kc_NO2 <- as.vector(kc_NO2_slice)

kc_NO2_df <- kc_NO2_df %>%
  filter(x < 100) %>%
  filter(y < 100) %>%
  filter(!is.na(kc_NO2)

         print("creating plot.")
         # Create the scatter plot
         p <- ggplot(kc_NO2_df, aes(x = x, y = y, color = kc_NO2)) +
           geom_point() +
           scale_color_viridis_c() +  # Use a continuous color scale
           labs(title = "Scatter Plot of kc_NO2",
                x = "X Coordinate",
                y = "Y Coordinate",
                color = "kc_NO2\n(ppm)") +
           theme_minimal()

         print("calling ggsave.")
         ggsave("./plots/test-scatter.png", p, width = 10, height = 8, dpi = 100)
         # Close the NetCDF file after reading
         nc_close(ncfile)