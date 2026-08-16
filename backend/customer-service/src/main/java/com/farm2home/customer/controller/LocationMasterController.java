package com.farm2home.customer.controller;

import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.customer.dto.request.LocationMasterRequest;
import com.farm2home.customer.dto.response.LocationMasterResponse;
import com.farm2home.customer.service.LocationMasterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Administration-only CRUD for the existing India reference tables. Public address dropdowns
 * remain on LocationController and return active records only. */
@RestController @RequestMapping("/api/v1/location-master") @RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "')")
public class LocationMasterController {
 private final LocationMasterService service;
 @GetMapping("/states") public ApiResponse<List<LocationMasterResponse>> states(@RequestParam(required=false) String search){return ApiResponse.success("States retrieved",service.states(search));}
 @PostMapping("/states") @ResponseStatus(HttpStatus.CREATED) public ApiResponse<LocationMasterResponse> createState(@Valid @RequestBody LocationMasterRequest r){return ApiResponse.success("State created",service.createState(r));}
 @PutMapping("/states/{id}") public ApiResponse<LocationMasterResponse> updateState(@PathVariable UUID id,@Valid @RequestBody LocationMasterRequest r){return ApiResponse.success("State updated",service.updateState(id,r));}
 @PatchMapping("/states/{id}") public ApiResponse<LocationMasterResponse> patchState(@PathVariable UUID id,@Valid @RequestBody LocationMasterRequest r){return ApiResponse.success("State updated",service.updateState(id,r));}
 @DeleteMapping("/states/{id}") public ApiResponse<Void> deleteState(@PathVariable UUID id){service.deleteState(id);return ApiResponse.success("State deleted",null);}
 @GetMapping("/districts") public ApiResponse<List<LocationMasterResponse>> districts(@RequestParam(required=false) UUID stateId,@RequestParam(required=false) String search){return ApiResponse.success("Districts retrieved",service.districts(stateId,search));}
 @PostMapping("/districts") @ResponseStatus(HttpStatus.CREATED) public ApiResponse<LocationMasterResponse> createDistrict(@Valid @RequestBody LocationMasterRequest r){return ApiResponse.success("District created",service.createDistrict(r));}
 @PutMapping("/districts/{id}") public ApiResponse<LocationMasterResponse> updateDistrict(@PathVariable UUID id,@Valid @RequestBody LocationMasterRequest r){return ApiResponse.success("District updated",service.updateDistrict(id,r));}
 @PatchMapping("/districts/{id}") public ApiResponse<LocationMasterResponse> patchDistrict(@PathVariable UUID id,@Valid @RequestBody LocationMasterRequest r){return ApiResponse.success("District updated",service.updateDistrict(id,r));}
 @DeleteMapping("/districts/{id}") public ApiResponse<Void> deleteDistrict(@PathVariable UUID id){service.deleteDistrict(id);return ApiResponse.success("District deleted",null);}
 @GetMapping("/cities") public ApiResponse<List<LocationMasterResponse>> cities(@RequestParam(required=false) UUID stateId,@RequestParam(required=false) UUID districtId,@RequestParam(required=false) String search){return ApiResponse.success("Cities retrieved",service.cities(stateId,districtId,search));}
 @PostMapping("/cities") @ResponseStatus(HttpStatus.CREATED) public ApiResponse<LocationMasterResponse> createCity(@Valid @RequestBody LocationMasterRequest r){return ApiResponse.success("City created",service.createCity(r));}
 @PutMapping("/cities/{id}") public ApiResponse<LocationMasterResponse> updateCity(@PathVariable UUID id,@Valid @RequestBody LocationMasterRequest r){return ApiResponse.success("City updated",service.updateCity(id,r));}
 @PatchMapping("/cities/{id}") public ApiResponse<LocationMasterResponse> patchCity(@PathVariable UUID id,@Valid @RequestBody LocationMasterRequest r){return ApiResponse.success("City updated",service.updateCity(id,r));}
 @DeleteMapping("/cities/{id}") public ApiResponse<Void> deleteCity(@PathVariable UUID id){service.deleteCity(id);return ApiResponse.success("City deleted",null);}
}
